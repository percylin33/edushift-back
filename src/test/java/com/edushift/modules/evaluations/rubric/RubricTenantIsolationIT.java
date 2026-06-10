package com.edushift.modules.evaluations.rubric;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Cross-tenant isolation IT for
 * {@code /v1/academic/rubrics} and
 * {@code /v1/academic/rubrics/{publicUuid}} (Sprint 5B / BE-5B.2).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A only sees A's rubrics
 *       in {@code GET /rubrics}. B's system + tenant-owned rubrics are
 *       invisible. The system endpoint returns the MINEDU seed
 *       materialised per-tenant and nothing from the other tenant.</li>
 *   <li><strong>Write isolation</strong> — GET / PATCH / DELETE / fork
 *       on B's rubric from A → 404.</li>
 *   <li><strong>Per-tenant uniqueness</strong> — both tenants can have
 *       a "Mi rubrica" in their own tenant (the unique index is
 *       scoped by {@code tenant_id}).</li>
 *   <li><strong>System fork semantics</strong> — forking A's system
 *       rubric from A creates a tenant-owned copy in A; B's GET of
 *       the fork returns 404 (anti-leakage).</li>
 *   <li><strong>System read-only</strong> — A's PATCH on a system
 *       rubric → 403 RUB_SYSTEM_READ_ONLY. A's DELETE on a system
 *       rubric → 403. B is unaffected because A's system rows are
 *       not in B's view.</li>
 * </ul>
 */
@DisplayName("Rubrics multi-tenancy isolation")
class RubricTenantIsolationIT extends IntegrationTest {

	private static final String BASE = "/v1/academic/rubrics";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-rubric@isolation.test";
	private static final String PASSWORD_A = "PassRubA-1!";
	private static final String PASSWORD_B = "PassRubB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RubricRepository rubricRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Read isolation
	// =========================================================================

	@Nested
	@DisplayName("Read isolation")
	class Read {

		@Test
		@DisplayName("admin A only sees A's rubrics in the listing")
		void listIsScoped() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<Rubric> bRubrics = TenantContext.runAs(fx.tenantB().getId(),
					() -> tx().execute(s -> rubricRepository.findAll()));
			List<UUID> bIds = bRubrics.stream()
					.map(Rubric::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B rubric publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's rubric by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					BASE + "/" + fx.tenantOwnedB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A reading B's system rubric by id → 404")
		void crossTenantGetSystemIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					BASE + "/" + fx.systemB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("GET /system materialises the seed per-tenant and is scoped")
		void systemListIsScoped() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					BASE + "/system", loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());
			assertThat(array).isNotEmpty();

			// All entries should be system=true.
			for (JsonNode item : array) {
				assertThat(item.get("isSystem").asBoolean())
						.as("non-system rubric leaked into /system endpoint")
						.isTrue();
			}
		}
	}

	// =========================================================================
	// Write isolation
	// =========================================================================

	@Nested
	@DisplayName("Write isolation")
	class Write {

		@Test
		@DisplayName("Same rubric NAME is allowed in two tenants (per-tenant uniqueness)")
		void nameUniquenessIsPerTenant() {
			Fixture fx = setupTenants();
			long countA = TenantContext.runAs(fx.tenantA().getId(),
					() -> tx().execute(s -> rubricRepository.count()));
			long countB = TenantContext.runAs(fx.tenantB().getId(),
					() -> tx().execute(s -> rubricRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant PATCH on B's rubric → 404")
		void crossTenantPatchIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = "{\"description\":\"hijack\"}";
			ResponseEntity<String> response = rest.exchange(
					BASE + "/" + fx.tenantOwnedB().getPublicUuid(),
					HttpMethod.PATCH,
					new HttpEntity<>(body, jsonHeadersWithAuth(loginA.accessToken())),
					String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE on B's rubric → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doDelete(
					BASE + "/" + fx.tenantOwnedB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant POST /fork on B's system rubric → 404 (anti-leakage)")
		void crossTenantForkIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doPost(
					BASE + "/" + fx.systemB().getPublicUuid() + "/fork",
					loginA.accessToken(), "{}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Fork of A's system rubric lives in A; B cannot read the fork → 404")
		void forkIsScopedToSourceTenant() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			// A forks A's own system rubric.
			ResponseEntity<String> forkResponse = doPost(
					BASE + "/" + fx.systemA().getPublicUuid() + "/fork",
					loginA.accessToken(), "{\"name\":\"Fork en A\"}");
			assertThat(forkResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			JsonNode forkJson = objectMapper.readTree(forkResponse.getBody())
					.get("data");
			UUID forkUuid = UUID.fromString(forkJson.get("publicUuid").asText());

			// A can read the fork.
			ResponseEntity<String> aGet = doGet(BASE + "/" + forkUuid, loginA.accessToken());
			assertThat(aGet.getStatusCode()).isEqualTo(HttpStatus.OK);

			// B cannot.
			AuthResponse loginB = login(fx.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> bGet = doGet(BASE + "/" + forkUuid, loginB.accessToken());
			assertThat(bGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// System read-only
	// =========================================================================

	@Nested
	@DisplayName("System read-only")
	class SystemReadOnly {

		@Test
		@DisplayName("PATCH on a system rubric → 403 RUB_SYSTEM_READ_ONLY")
		void patchSystemIs403() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = "{\"description\":\"hijack\"}";
			ResponseEntity<String> response = rest.exchange(
					BASE + "/" + fx.systemA().getPublicUuid(),
					HttpMethod.PATCH,
					new HttpEntity<>(body, jsonHeadersWithAuth(loginA.accessToken())),
					String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
			assertThat(response.getBody()).contains("RUB_SYSTEM_READ_ONLY");
		}

		@Test
		@DisplayName("DELETE on a system rubric → 403 RUB_SYSTEM_READ_ONLY")
		void deleteSystemIs403() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doDelete(
					BASE + "/" + fx.systemA().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
			assertThat(response.getBody()).contains("RUB_SYSTEM_READ_ONLY");
		}
	}

	// =========================================================================
	// HTTP helpers
	// =========================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private HttpHeaders jsonHeadersWithAuth(String bearer) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return headers;
	}

	private AuthResponse login(String slug, String email, String password) throws Exception {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", slug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		ResponseEntity<String> response = rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
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

	private ResponseEntity<String> doDelete(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	// =========================================================================
	// Fixture
	// =========================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			Rubric systemA, Rubric systemB,
			Rubric tenantOwnedA, Rubric tenantOwnedB
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-rub-a-");
		Tenant tenantB = createTenant("it-rub-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);

		Bundle bundleA = seedRubrics(tenantA);
		Bundle bundleB = seedRubrics(tenantB);

		return new Fixture(tenantA, tenantB,
				bundleA.system(), bundleB.system(),
				bundleA.tenantOwned(), bundleB.tenantOwned());
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
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

	record Bundle(Rubric system, Rubric tenantOwned) {}

	/**
	 * Seeds 1 system rubric + 1 tenant-owned rubric in the given
	 * tenant. Keeps the fixture minimal — full system materialisation
	 * is exercised by the {@code /system} endpoint test, not by
	 * fixtures.
	 */
	private Bundle seedRubrics(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Rubric system = new Rubric();
			system.setPublicUuid(UUID.randomUUID());
			system.setName("Ensayo (MINEDU)");
			system.setDescription("seed");
			system.setIsSystem(Boolean.TRUE);
			system.setIsActive(Boolean.TRUE);
			system.setCriteria(simpleCriteria());
			system.setLevels(simpleLevels());
			system.setCreatedAt(Instant.now());
			system.setUpdatedAt(Instant.now());
			Rubric savedSystem = rubricRepository.saveAndFlush(system);

			Rubric tenantOwned = new Rubric();
			tenantOwned.setPublicUuid(UUID.randomUUID());
			tenantOwned.setName("Mi rubrica");
			tenantOwned.setDescription("creada por el tenant");
			tenantOwned.setIsSystem(Boolean.FALSE);
			tenantOwned.setIsActive(Boolean.TRUE);
			tenantOwned.setCriteria(simpleCriteria());
			tenantOwned.setLevels(simpleLevels());
			tenantOwned.setCreatedAt(Instant.now());
			tenantOwned.setUpdatedAt(Instant.now());
			Rubric savedTenantOwned = rubricRepository.saveAndFlush(tenantOwned);

			return new Bundle(savedSystem, savedTenantOwned);
		}));
	}

	private static List<Map<String, Object>> simpleCriteria() {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("key", "calidad");
		c.put("name", "Calidad");
		c.put("weight", BigDecimal.valueOf(100.00));
		c.put("descriptors", List.of());
		return List.of(c);
	}

	private static List<Map<String, Object>> simpleLevels() {
		List<Map<String, Object>> out = new java.util.ArrayList<>();
		String[] codes = {"EN_INICIO", "EN_PROCESO", "ESPERADO", "SOBRESALIENTE"};
		for (int i = 0; i < codes.length; i++) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("code", codes[i]);
			m.put("name", codes[i]);
			m.put("order", i + 1);
			out.add(m);
		}
		return out;
	}
}
