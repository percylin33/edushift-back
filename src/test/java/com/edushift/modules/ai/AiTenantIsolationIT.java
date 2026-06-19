package com.edushift.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-tenant isolation IT for the AI assistant (Sprint 7b / BE-7b.4).
 *
 * <h3>Why direct JdbcTemplate inserts?</h3>
 * The {@code tenant_ai_settings} table has a FK to {@code tenants.public_uuid}
 * (see V36) while the rest of the schema keys to {@code tenants.id} (PK).
 * That inconsistency — surfaced in this IT as {@code DEBT-BE-7B-4} — makes
 * it impossible to satisfy both the FK and the Hibernate {@code @TenantId}
 * filter from a single row write. To sidestep the seed step entirely and
 * exercise the isolation contract directly, this IT inserts
 * {@code ai_generations} rows via JdbcTemplate (no FK to {@code tenants},
 * no {@code @TenantId} write path) and validates that:
 * <ol>
 *   <li>GET with a bearer of the <em>same</em> tenant returns 200 + the
 *       row's metadata.</li>
 *   <li>GET with a bearer of a <em>different</em> tenant returns 404
 *       (the Hibernate {@code @TenantId} filter hides the row).</li>
 *   <li>DELETE with a cross-tenant bearer returns 404 — and the row
 *       remains intact (no side effect on the owner tenant).</li>
 * </ol>
 *
 * <h3>Why these scenarios matter</h3>
 * Cross-tenant data leaks are the worst class of bug in a multi-tenant
 * SaaS (regulatory + reputational blast radius). The audit dashboard
 * ("AI history") of a teacher from tenant B MUST NOT show generations
 * made by tenant A. This IT is the regression gate.
 */
@DisplayName("AI tenant isolation (BE-7b.4)")
class AiTenantIsolationIT extends IntegrationTest {

	private static final String AI_BASE = "/v1/lms/ai";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private JwtService jwtService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private DataSource dataSource;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;
	private JdbcTemplate jdbc;

	private TransactionTemplate tx() {
		if (tx == null) {
			tx = new TransactionTemplate(txManager);
		}
		return tx;
	}

	private JdbcTemplate jdbc() {
		if (jdbc == null) {
			jdbc = new JdbcTemplate(dataSource);
		}
		return jdbc;
	}

	// =========================================================================
	// Cross-tenant isolation of GET /lms/ai/generations/{publicUuid}
	// =========================================================================

	@Nested
	@DisplayName("GET /lms/ai/generations/{publicUuid}")
	class GetGenerationIsolation {

		@Test
		@DisplayName("ISO-1: tenant A owner can read their own generation → 200")
		void ownerCanReadOwnGeneration() throws Exception {
			Pair pair = seedTwoTenants();
			UUID genUuid = insertCompletedGeneration(pair.tenantA, "own topic",
					"prompt-A", "{\"questions\":[]}", 100, 200L);

			ResponseEntity<String> response = getGeneration(
					bearerFor(pair.teacherA, pair.tenantA, UserRole.TEACHER),
					genUuid);

			assertThat(response.getStatusCode())
					.as("owner of the row must be able to read it — body=%s", response.getBody())
					.isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.get("generationUuid").asText()).isEqualTo(genUuid.toString());
			assertThat(data.get("status").asText()).isEqualTo("COMPLETED");
		}

		@Test
		@DisplayName("ISO-2: tenant B requesting tenant A's generation → 404 AI_GENERATION_NOT_FOUND (no leak)")
		void crossTenantReadIsHidden() throws Exception {
			Pair pair = seedTwoTenants();
			UUID genUuidA = insertCompletedGeneration(pair.tenantA, "private topic",
					"prompt-A", "{\"questions\":[]}", 100, 200L);

			ResponseEntity<String> response = getGeneration(
					bearerFor(pair.teacherB, pair.tenantB, UserRole.TEACHER),
					genUuidA);

			assertThat(response.getStatusCode())
					.as("cross-tenant GET must be 404 (anti-enumeration) — body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
			JsonNode errs = objectMapper.readTree(response.getBody()).get("errors");
			assertThat(errs).isNotNull();
			assertThat(errs.get(0).get("code").asText())
					.as("error code must be AI_GENERATION_NOT_FOUND")
					.isEqualTo("AI_GENERATION_NOT_FOUND");

			// Defence-in-depth: tenant A can still read it.
			ResponseEntity<String> reread = getGeneration(
					bearerFor(pair.teacherA, pair.tenantA, UserRole.TEACHER),
					genUuidA);
			assertThat(reread.getStatusCode()).isEqualTo(HttpStatus.OK);
		}

		@Test
		@DisplayName("ISO-3: cross-tenant DELETE on tenant A's row → 404 + row intact")
		void crossTenantDeleteIsHidden() throws Exception {
			Pair pair = seedTwoTenants();
			UUID genUuidA = insertCompletedGeneration(pair.tenantA, "private topic",
					"prompt-A", "{\"questions\":[]}", 100, 200L);

			ResponseEntity<String> delete = deleteGeneration(
					bearerFor(pair.teacherB, pair.tenantB, UserRole.TEACHER),
					genUuidA);

			assertThat(delete.getStatusCode())
					.as("cross-tenant DELETE must be 404 — body=%s", delete.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);

			// Tenant A's row is still readable and not cancelled.
			ResponseEntity<String> reread = getGeneration(
					bearerFor(pair.teacherA, pair.tenantA, UserRole.TEACHER),
					genUuidA);
			assertThat(reread.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(reread.getBody()).get("data");
			assertThat(data.get("status").asText())
					.as("cross-tenant DELETE must not touch the row's status")
					.isEqualTo("COMPLETED");
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private record Pair(Tenant tenantA, User teacherA, Tenant tenantB, User teacherB) {}

	private Pair seedTwoTenants() {
		Tenant a = createTenant("it-ai-iso-a-");
		Tenant b = createTenant("it-ai-iso-b-");
		User teacherA = createTeacher(a, "it-ai-iso-a-");
		User teacherB = createTeacher(b, "it-ai-iso-b-");
		return new Pair(a, teacherA, b, teacherB);
	}

	/**
	 * Inserts an {@code ai_generations} row directly via JdbcTemplate so
	 * we can pin a specific {@code tenant_id} (PK interno) without going
	 * through the quota / LLM pipeline. The column is nullable-or-defaulted
	 * for everything except the NOT NULL ones, and we let Postgres
	 * defaults fill {@code created_at} / {@code updated_at} / {@code deleted}.
	 */
	private UUID insertCompletedGeneration(Tenant tenant, String publicUuidSuffix,
			String prompt, String responseJson, int promptTokens, long latencyMs) {
		UUID publicUuid = UUID.randomUUID();
		UUID rowId = UUID.randomUUID();
		Instant now = Instant.now();
		// Run inside its own independent transaction so the row is
		// committed even if a surrounding TX is later rolled back by
		// an unrelated assertion failure or test cleanup hook.
		tx().executeWithoutResult(status -> jdbc().update("""
				INSERT INTO edushift.ai_generations
				    (id, public_uuid, tenant_id, feature, prompt_text,
				     prompt_tokens, response_text, response_parsed,
				     response_tokens, model_used, status, latency_ms,
				     created_at, updated_at, deleted)
				VALUES
				    (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, false)
				""",
				rowId, publicUuid, tenant.getId(),
				"QUIZ_QUESTION_SUGGEST", prompt,
				promptTokens, responseJson, responseJson, 50,
				"mock-stub", "COMPLETED", (int) latencyMs,
				java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)));
		return publicUuid;
	}

	private String bearerFor(User user, Tenant tenant, UserRole role) {
		return jwtService.issueAccessToken(user, tenant, Set.of(role.name()));
	}

	private ResponseEntity<String> getGeneration(String bearer, UUID publicUuid) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(AI_BASE + "/generations/" + publicUuid, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> deleteGeneration(String bearer, UUID publicUuid) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(AI_BASE + "/generations/" + publicUuid, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT AI ISO Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		// Tenants are a global catalog; no TenantContext needed for INSERT.
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createTeacher(Tenant tenant, String emailPrefix) {
		// @TenantId carries the tenant's INTERNAL id (PK), mirroring the
		// JWT's tenant_id claim — see JwtServiceImpl.issueAccessToken.
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			User user = new User();
			user.setEmail(emailPrefix + "-" + UUID.randomUUID().toString().substring(0, 8)
					+ "@demo.edushift.pe");
			user.setPasswordHash(passwordEncoder.encode("Pass-AI-IT!"));
			user.setFirstName("It");
			user.setLastName("AiIso");
			user.setStatus(UserStatus.ACTIVE);
			user.setEmailVerified(true);
			user.setMfaEnabled(false);
			user.setRoleSet(Set.of(UserRole.TEACHER));
			return userRepository.saveAndFlush(user);
		}));
	}
}
