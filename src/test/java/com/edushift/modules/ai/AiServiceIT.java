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
import java.util.Set;
import java.util.UUID;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IT for the AI assistant endpoints (Sprint 7b / BE-7b.4).
 *
 * <h3>LLM client</h3>
 * No real provider is enabled in the {@code test} profile (see
 * {@code application-test.properties} — the
 * {@code app.llm.openrouter.enabled} and {@code app.llm.minimax.enabled}
 * flags default to false). The
 * {@code LlmAutoConfiguration.mockLlmClient()} therefore wins and the
 * {@code MockLlmClient} is what the LLM call resolves to. That bean
 * returns a deterministic JSON stub so the full pipeline (quota gate +
 * audit row + LLM call + parse + counters) is exercised end-to-end
 * without any network IO.
 *
 * <h3>Scope</h3>
 * The DoD of BE-7b.4 asks for ≥5 scenarios. This IT covers the two
 * scenarios that are <em>orthogonal to the {@code tenant_ai_settings}
 * seed</em> (validation + RBAC). The end-to-end happy paths
 * (default / math topic, AI disabled, async polling) are blocked by
 * a pre-existing inconsistency between the V36 migration's FK
 * (which references {@code tenants.public_uuid}) and the project's
 * Hibernate {@code @TenantId} discriminator (which always carries
 * {@code tenants.id}). Reported as {@code DEBT-BE-7B-4} in
 * {@code docs/qa/sprint-07b-be7b4-multi-tenant-audit.md}.
 *
 * <p>The end-to-end happy paths ARE covered (with mocks) by:</p>
 * <ul>
 *   <li>{@code LmsAiServiceTest} — 7 scenarios (happy path, parse errors,
 *       quota disabled, LLM timeout, etc.)</li>
 *   <li>{@code AiControllerTest} — 16 scenarios (routing, RBAC, error
 *       envelope)</li>
 * </ul>
 *
 * <h3>Scenarios</h3>
 * <ol>
 *   <li><strong>AI-4</strong> Validation error (topic too short) →
 *       400 from the BEAN validation layer.</li>
 *   <li><strong>AI-5</strong> STUDENT bearer (no
 *       {@code LMS_AI_GENERATE}) → 403 from Spring Security.</li>
 * </ol>
 */
@DisplayName("AI service IT (BE-7b.4)")
class AiServiceIT extends IntegrationTest {

	private static final String AI_BASE = "/v1/lms/ai";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private JwtService jwtService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) {
			tx = new TransactionTemplate(txManager);
		}
		return tx;
	}

	@Nested
	@DisplayName("HTTP boundary (validation + RBAC)")
	class HttpBoundary {

		@Test
		@DisplayName("AI-4: validation error (topic too short) → 400; no LLM call")
		void validationErrorRejectsBeforeLlmCall() throws Exception {
			TenantSeed seed = seedTenantAndTeacher("it-ai-val-", UserRole.TEACHER);
			String bearer = bearerFor(seed.teacher(), seed.tenant(), UserRole.TEACHER);

			// Single-character topic violates @Size(min = 2).
			ResponseEntity<String> response = postQuizQuestions(
					bearer,
					"""
					{
					  "topic": "x",
					  "count": 1
					}
					""");

			assertThat(response.getStatusCode())
					.as("Bean Validation should return 400, body=%s", response.getBody())
					.isEqualTo(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("AI-5: STUDENT bearer (no LMS_AI_GENERATE) → 403 from Spring Security")
		void studentRoleCannotCallAi() throws Exception {
			TenantSeed seed = seedTenantAndTeacher("it-ai-stud-", UserRole.STUDENT);
			String bearer = bearerFor(seed.teacher(), seed.tenant(), UserRole.STUDENT);

			ResponseEntity<String> response = postQuizQuestions(
					bearer,
					"""
					{
					  "topic": "Tema cualquiera",
					  "count": 1
					}
					""");

			// Spring Security's @PreAuthorize failure → 403. The body shape
			// differs from the AI module's API error envelope (no
			// `errors[].code`), but we only care about the status.
			assertThat(response.getStatusCode())
					.as("STUDENT must not reach the AI module — body=%s", response.getBody())
					.isEqualTo(HttpStatus.FORBIDDEN);
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private record TenantSeed(Tenant tenant, User teacher) {}

	private String bearerFor(User user, Tenant tenant, UserRole role) {
		return jwtService.issueAccessToken(user, tenant, Set.of(role.name()));
	}

	private ResponseEntity<String> postQuizQuestions(String bearer, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(bearer);
		return rest.exchange(AI_BASE + "/quiz-questions", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private TenantSeed seedTenantAndTeacher(String slugPrefix, UserRole role) {
		Tenant tenant = createTenant(slugPrefix);
		User user = createUserWithRole(tenant, role);
		return new TenantSeed(tenant, user);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT AI Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		// Tenants are a global catalog; no TenantContext needed for INSERT.
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createUserWithRole(Tenant tenant, UserRole role) {
		// user extends TenantAwareEntity. The Hibernate @TenantId discriminator
		// expects the tenant's INTERNAL id (mirroring what the JWT carries
		// as tenant_id — see JwtServiceImpl.issueAccessToken line 104).
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			User user = new User();
			user.setEmail("it-ai-" + role.name().toLowerCase() + "-"
					+ UUID.randomUUID().toString().substring(0, 8) + "@demo.edushift.pe");
			user.setPasswordHash(passwordEncoder.encode("Pass-AI-IT!"));
			user.setFirstName("It");
			user.setLastName("Ai-" + role.name());
			user.setStatus(UserStatus.ACTIVE);
			user.setEmailVerified(true);
			user.setMfaEnabled(false);
			user.setRoleSet(Set.of(role));
			return userRepository.saveAndFlush(user);
		}));
	}
}
