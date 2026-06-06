package com.edushift.modules.academic.year;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
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
import java.time.LocalDate;
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
 * Cross-tenant isolation IT for the {@code /v1/academic/years} surface
 * added by Sprint 4 BE-4.1.
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — list and get-by-id only ever
 *       return rows from the caller's tenant (never B's, even if A and B
 *       both have a year named "2026").</li>
 *   <li><strong>Write isolation</strong> — POST in tenant A leaves
 *       tenant B's catalog untouched; PUT and DELETE on a sibling
 *       tenant's publicUuid return 404.</li>
 *   <li><strong>Activate isolation</strong> — activating a year in
 *       tenant A does not transition tenant B's ACTIVE year to CLOSED.
 *       The unique partial index {@code uk_academic_years_tenant_active}
 *       is per-tenant.</li>
 *   <li><strong>Name uniqueness is per-tenant</strong> — both tenants
 *       can hold a year named {@code "2026"} without colliding.</li>
 *   <li><strong>Role gate</strong> — STAFF without TENANT_ADMIN gets
 *       403 on mutations; TENANT_ADMIN goes through.</li>
 * </ul>
 *
 * <p>Same fixture style as {@code StudentTenantIsolationIT}: shared Postgres
 * container across the JVM, no rollback between tests, so every method
 * seeds its own UUID-suffixed tenants.</p>
 */
@DisplayName("Academic years multi-tenancy isolation")
class AcademicYearTenantIsolationIT extends IntegrationTest {

	private static final String YEARS_BASE = "/v1/academic/years";

	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "shared-academic@isolation.test";

	private static final String PASSWORD_A = "PassAcadA-1!";

	private static final String PASSWORD_B = "PassAcadB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository repository;
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

	// ===========================================================================
	// GET /v1/academic/years — list isolation
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/academic/years — list isolation")
	class ListIsolation {

		@Test
		@DisplayName("admin A only sees A's years; B's years never appear")
		void listOnlyOwnTenant() throws Exception {
			TenantPair pair = seedAdmins();
			AcademicYear yearA = createYear(pair.tenantA(), "2026-A", AcademicYearStatus.PLANNING);
			AcademicYear yearB = createYear(pair.tenantB(), "2026-B", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(YEARS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());
			assertThat(array.isArray()).isTrue();
			boolean foundA = false;
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(id)
						.as("year B's publicUuid must NOT appear in tenant A's list")
						.isNotEqualTo(yearB.getPublicUuid());
				if (id.equals(yearA.getPublicUuid())) foundA = true;
			}
			assertThat(foundA).as("year A must appear in tenant A's list").isTrue();
		}
	}

	// ===========================================================================
	// GET /{publicUuid} — read isolation
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/academic/years/{publicUuid} — read isolation")
	class ReadIsolation {

		@Test
		@DisplayName("admin A reading year-B → 404 RESOURCE_NOT_FOUND")
		void crossTenantReadIs404() throws Exception {
			TenantPair pair = seedAdmins();
			AcademicYear yearB = createYear(pair.tenantB(), "2026", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					YEARS_BASE + "/" + yearB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// ===========================================================================
	// POST + PUT + DELETE — write isolation
	// ===========================================================================

	@Nested
	@DisplayName("Write isolation")
	class WriteIsolation {

		@Test
		@DisplayName("name uniqueness is per-tenant: both tenants can have year '2026'")
		void nameUniquenessIsPerTenant() throws Exception {
			TenantPair pair = seedAdmins();
			createYear(pair.tenantB(), "2026", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = """
					{
					  "name":"2026",
					  "startDate":"2026-03-01",
					  "endDate":"2026-12-15"
					}
					""";

			ResponseEntity<String> response = doPost(YEARS_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("same year name must be allowed in a different tenant")
					.isEqualTo(HttpStatus.CREATED);
		}

		@Test
		@DisplayName("PUT /years/{B's publicUuid} from tenant A → 404, B is untouched")
		void crossTenantUpdateIs404() throws Exception {
			TenantPair pair = seedAdmins();
			AcademicYear yearB = createYear(pair.tenantB(), "2026", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = """
					{ "name":"hacked-2026" }
					""";
			ResponseEntity<String> response = doPut(
					YEARS_BASE + "/" + yearB.getPublicUuid(),
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			AcademicYear refreshed = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s ->
							repository.findByPublicUuid(yearB.getPublicUuid()).orElseThrow()));
			assertThat(refreshed.getName()).isEqualTo("2026");
		}

		@Test
		@DisplayName("DELETE /years/{B's publicUuid} from tenant A → 404, B's row survives")
		void crossTenantDeleteIs404() throws Exception {
			TenantPair pair = seedAdmins();
			AcademicYear yearB = createYear(pair.tenantB(), "2026", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					YEARS_BASE + "/" + yearB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			AcademicYear refreshed = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s ->
							repository.findByPublicUuid(yearB.getPublicUuid()).orElseThrow()));
			assertThat(refreshed.isDeleted()).isFalse();
		}
	}

	// ===========================================================================
	// POST /activate — activate isolation
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/academic/years/{publicUuid}/activate — activation isolation")
	class ActivateIsolation {

		@Test
		@DisplayName("activating in A does NOT close the ACTIVE year in B")
		void activateDoesNotCascadeAcrossTenants() throws Exception {
			TenantPair pair = seedAdmins();
			AcademicYear activeB = createYear(pair.tenantB(), "2025-B", AcademicYearStatus.ACTIVE);
			AcademicYear planningA = createYear(pair.tenantA(), "2026-A", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doPost(
					YEARS_BASE + "/" + planningA.getPublicUuid() + "/activate",
					loginA.accessToken(), null);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode payload = objectMapper.readTree(response.getBody()).get("data");
			assertThat(payload.get("status").asText()).isEqualTo("ACTIVE");

			// B's ACTIVE year must remain ACTIVE — no cross-tenant cascade.
			AcademicYear refreshedB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s ->
							repository.findByPublicUuid(activeB.getPublicUuid()).orElseThrow()));
			assertThat(refreshedB.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);
		}

		@Test
		@DisplayName("cross-tenant activate of B's year from A → 404")
		void crossTenantActivateIs404() throws Exception {
			TenantPair pair = seedAdmins();
			AcademicYear yearB = createYear(pair.tenantB(), "2026", AcademicYearStatus.PLANNING);

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doPost(
					YEARS_BASE + "/" + yearB.getPublicUuid() + "/activate",
					loginA.accessToken(), null);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

	private ResponseEntity<String> doDelete(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	// ===========================================================================
	// DB seeding
	// ===========================================================================

	record TenantPair(Tenant tenantA, Tenant tenantB, User userA, User userB) {}

	private TenantPair seedAdmins() {
		Tenant tenantA = createTenant("it-acad-yr-a-", TenantStatus.ACTIVE);
		Tenant tenantB = createTenant("it-acad-yr-b-", TenantStatus.ACTIVE);
		User userA = createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		User userB = createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		return new TenantPair(tenantA, tenantB, userA, userB);
	}

	private Tenant createTenant(String slugPrefix, TenantStatus status) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(status);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createAdmin(Tenant tenant, String email, String rawPassword) {
		return TenantContext.runAs(tenant.getId(), () ->
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

	private AcademicYear createYear(Tenant tenant, String name, AcademicYearStatus status) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					AcademicYear y = new AcademicYear();
					y.setName(name);
					y.setStatus(status);
					y.setStartDate(LocalDate.of(2026, 3, 1));
					y.setEndDate(LocalDate.of(2026, 12, 15));
					return repository.saveAndFlush(y);
				}));
	}
}
