package com.edushift.modules.academic.levelgrade;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.config.AcademicDefaults;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * Cross-tenant isolation IT for {@code /v1/academic/levels} +
 * {@code /v1/academic/levels/{id}/grades} (Sprint 4 / BE-4.2).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Seed isolation</strong> — invoking
 *       {@link AcademicSeedService#seedDefaults} on tenant A populates
 *       only A's catalog; B remains untouched.</li>
 *   <li><strong>Read isolation</strong> — admin A's GET only sees A's
 *       levels and grades; B's UUIDs never appear.</li>
 *   <li><strong>Cross-tenant access</strong> — admin A reading B's level
 *       (or grade) returns 404 (anti-enumeration).</li>
 *   <li><strong>Reorder isolation</strong> — reorder in A doesn't affect
 *       B's grade ordering.</li>
 *   <li><strong>Idempotent seed</strong> — calling seedDefaults twice on
 *       the same tenant doesn't double the catalog.</li>
 * </ul>
 */
@DisplayName("Levels & Grades multi-tenancy isolation")
class LevelGradeTenantIsolationIT extends IntegrationTest {

	private static final String LEVELS_BASE = "/v1/academic/levels";

	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "shared-lvlgrd@isolation.test";

	private static final String PASSWORD_A = "PassLvlA-1!";

	private static final String PASSWORD_B = "PassLvlB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// ===========================================================================
	// Seed
	// ===========================================================================

	@Nested
	@DisplayName("Seed defaults")
	class Seed {

		@Test
		@DisplayName("seedDefaults populates only the calling tenant's catalog")
		void seedIsolation() {
			TenantPair pair = seedAdmins();

			TenantContext.runAs(pair.tenantA().getId(),
					() -> tx().execute(s -> {
						seedService.seedDefaults(pair.tenantA().getId());
						return null;
					}));

			List<AcademicLevel> levelsInA = TenantContext.runAs(pair.tenantA().getId(),
					() -> tx().execute(s -> levelRepository.findAllByOrderByOrdinalAsc()));
			List<AcademicLevel> levelsInB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> levelRepository.findAllByOrderByOrdinalAsc()));

			assertThat(levelsInA).hasSize(AcademicDefaults.LEVELS.size());
			assertThat(levelsInB).isEmpty();
		}

		@Test
		@DisplayName("seedDefaults is idempotent — second call is a no-op")
		void seedIdempotent() {
			Tenant tenant = createTenant("it-lg-idem-", TenantStatus.ACTIVE);

			boolean first = TenantContext.runAs(tenant.getId(),
					() -> tx().execute(s -> seedService.seedDefaults(tenant.getId())));
			boolean second = TenantContext.runAs(tenant.getId(),
					() -> tx().execute(s -> seedService.seedDefaults(tenant.getId())));

			assertThat(first).isTrue();
			assertThat(second).isFalse();

			List<AcademicLevel> levels = TenantContext.runAs(tenant.getId(),
					() -> tx().execute(s -> levelRepository.findAllByOrderByOrdinalAsc()));
			assertThat(levels).hasSize(AcademicDefaults.LEVELS.size());
		}
	}

	// ===========================================================================
	// Read isolation
	// ===========================================================================

	@Nested
	@DisplayName("Read isolation")
	class ReadIsolation {

		@Test
		@DisplayName("admin A only sees A's levels (B's are seeded separately and invisible)")
		void listIsScoped() throws Exception {
			TenantPair pair = seedAdmins();
			seedDefaultsFor(pair.tenantA());
			seedDefaultsFor(pair.tenantB());

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(LEVELS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<AcademicLevel> bLevels = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> levelRepository.findAllByOrderByOrdinalAsc()));

			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				for (AcademicLevel bLevel : bLevels) {
					assertThat(id)
							.as("tenant B level publicUuid must NOT appear in tenant A's response")
							.isNotEqualTo(bLevel.getPublicUuid());
				}
			}
			assertThat(array).hasSize(AcademicDefaults.LEVELS.size());
		}

		@Test
		@DisplayName("admin A reading B's level by id → 404")
		void crossTenantGetIs404() throws Exception {
			TenantPair pair = seedAdmins();
			seedDefaultsFor(pair.tenantB());

			AcademicLevel anyLevelOfB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> levelRepository.findAllByOrderByOrdinalAsc().get(0)));

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					LEVELS_BASE + "/" + anyLevelOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// ===========================================================================
	// Write isolation
	// ===========================================================================

	@Nested
	@DisplayName("Write isolation")
	class WriteIsolation {

		@Test
		@DisplayName("Same level CODE is allowed in two tenants (uniqueness is per-tenant)")
		void codeUniquenessIsPerTenant() throws Exception {
			TenantPair pair = seedAdmins();
			seedDefaultsFor(pair.tenantB());

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = """
					{ "code":"INICIAL", "name":"Inicial", "ordinal":1 }
					""";

			ResponseEntity<String> response = doPost(LEVELS_BASE, loginA.accessToken(), body);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}

		@Test
		@DisplayName("PUT on B's level from A → 404")
		void crossTenantPutIs404() throws Exception {
			TenantPair pair = seedAdmins();
			seedDefaultsFor(pair.tenantB());

			AcademicLevel anyLevelOfB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> levelRepository.findAllByOrderByOrdinalAsc().get(0)));

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doPut(
					LEVELS_BASE + "/" + anyLevelOfB.getPublicUuid(),
					loginA.accessToken(),
					"{\"name\":\"hacked\"}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// ===========================================================================
	// Reorder isolation
	// ===========================================================================

	@Nested
	@DisplayName("Reorder isolation")
	class ReorderIsolation {

		@Test
		@DisplayName("Reordering grades in A does not affect B's ordering")
		void reorderDoesNotCascade() throws Exception {
			TenantPair pair = seedAdmins();
			seedDefaultsFor(pair.tenantA());
			seedDefaultsFor(pair.tenantB());

			// Pick PRIMARIA in A and reorder its grades
			AcademicLevel primariaA = TenantContext.runAs(pair.tenantA().getId(),
					() -> tx().execute(s -> levelRepository.findByCodeIgnoreCase("PRIMARIA").orElseThrow()));
			List<Grade> gradesA = TenantContext.runAs(pair.tenantA().getId(),
					() -> tx().execute(s -> gradeRepository.findAllByLevelOrderByOrdinalAsc(primariaA)));

			// Snapshot B's PRIMARIA before for later comparison
			AcademicLevel primariaB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> levelRepository.findByCodeIgnoreCase("PRIMARIA").orElseThrow()));
			List<Grade> gradesBBefore = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> gradeRepository.findAllByLevelOrderByOrdinalAsc(primariaB)));
			List<UUID> bOrderBefore = gradesBBefore.stream().map(Grade::getPublicUuid).toList();

			// Reverse the ordering in A
			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			StringBuilder items = new StringBuilder("[");
			for (int i = 0; i < gradesA.size(); i++) {
				if (i > 0) items.append(",");
				int newOrdinal = gradesA.size() - i; // reverse order
				items.append("{\"publicUuid\":\"")
						.append(gradesA.get(i).getPublicUuid())
						.append("\",\"ordinal\":").append(newOrdinal).append("}");
			}
			items.append("]");
			String body = "{\"items\":" + items + "}";

			ResponseEntity<String> response = doPatch(
					LEVELS_BASE + "/" + primariaA.getPublicUuid() + "/grades/reorder",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			// B's grades stayed exactly as before
			List<Grade> gradesBAfter = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> gradeRepository.findAllByLevelOrderByOrdinalAsc(primariaB)));
			List<UUID> bOrderAfter = gradesBAfter.stream().map(Grade::getPublicUuid).toList();

			assertThat(bOrderAfter).isEqualTo(bOrderBefore);
		}
	}

	// ===========================================================================
	// HTTP helpers
	// ===========================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private ResponseEntity<String> doLogin(String slug, String email, String password) {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", slug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		return rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private AuthResponse login(String slug, String email, String password) throws Exception {
		ResponseEntity<String> response = doLogin(slug, email, password);
		assertThat(response.getStatusCode())
				.as("seed login() requires HTTP 200; body=%s", response.getBody())
				.isEqualTo(HttpStatus.OK);
		return objectMapper.readValue(response.getBody(), AuthResponse.class);
	}

	private ResponseEntity<String> doGet(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> doPost(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doPut(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PUT,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doPatch(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PATCH,
				new HttpEntity<>(body, headers), String.class);
	}

	// ===========================================================================
	// DB seeding
	// ===========================================================================

	record TenantPair(Tenant tenantA, Tenant tenantB) {}

	private TenantPair seedAdmins() {
		Tenant tenantA = createTenant("it-lg-a-", TenantStatus.ACTIVE);
		Tenant tenantB = createTenant("it-lg-b-", TenantStatus.ACTIVE);
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		return new TenantPair(tenantA, tenantB);
	}

	private Tenant createTenant(String slugPrefix, TenantStatus status) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(status);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private void createAdmin(Tenant tenant, String email, String rawPassword) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash(passwordEncoder.encode(rawPassword));
					user.setFirstName("It");
					user.setLastName(tenant.getSlug());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.TENANT_ADMIN);
					return userRepository.saveAndFlush(user);
				}));
	}

	private void seedDefaultsFor(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					seedService.seedDefaults(tenant.getId());
					return null;
				}));
	}
}
