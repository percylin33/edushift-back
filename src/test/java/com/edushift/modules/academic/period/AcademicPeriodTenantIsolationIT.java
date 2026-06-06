package com.edushift.modules.academic.period;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
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
 * Cross-tenant isolation IT for {@code /v1/academic/periods}
 * (Sprint 4 / BE-4.5).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A only sees A's
 *       periods; B's never appear.</li>
 *   <li><strong>Cross-tenant access</strong> — GET / DELETE on B's
 *       period from A → 404.</li>
 *   <li><strong>Overlap is per-tenant</strong> — same date range works
 *       in both tenants without colliding.</li>
 *   <li><strong>Cross-tenant year reference on create</strong> →
 *       404 RESOURCE_NOT_FOUND (anti-enumeration).</li>
 * </ul>
 */
@DisplayName("AcademicPeriod multi-tenancy isolation")
class AcademicPeriodTenantIsolationIT extends IntegrationTest {

	private static final String PERIODS_BASE = "/v1/academic/periods";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-period@isolation.test";
	private static final String PASSWORD_A = "PassPeriodA-1!";
	private static final String PASSWORD_B = "PassPeriodB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicPeriodRepository periodRepository;
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
		@DisplayName("admin A only sees A's periods")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(PERIODS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<AcademicPeriod> bPeriods = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> periodRepository.findAll()));
			List<UUID> bIds = bPeriods.stream().map(AcademicPeriod::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B period publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's period by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			AcademicPeriod anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> periodRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					PERIODS_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// Write isolation
	// =========================================================================

	@Nested
	@DisplayName("Write isolation")
	class Write {

		@Test
		@DisplayName("Same overlapping range works independently in two tenants")
		void overlapIsPerTenant() {
			Fixture fixture = setupTenants();

			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> periodRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> periodRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant year reference on create → 404")
		void crossTenantYearIs404() throws Exception {
			Fixture fixture = setupTenants();

			AcademicYear yearOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> yearRepository.findFirstByStatus(
							AcademicYearStatus.ACTIVE).orElseThrow()));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = """
					{
					  "academicYearPublicUuid": "%s",
					  "periodType": "TRIMESTRE",
					  "ordinal": 1,
					  "startDate": "2026-03-01",
					  "endDate": "2026-05-15"
					}""".formatted(yearOfB.getPublicUuid());

			ResponseEntity<String> response = doPost(PERIODS_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			AcademicPeriod anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> periodRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					PERIODS_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
	// Fixtures
	// =========================================================================

	record Fixture(Tenant tenantA, Tenant tenantB) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-period-a-");
		Tenant tenantB = createTenant("it-period-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);

		// Each tenant gets the same active year (2026) and 2 BIMESTRES with the
		// same date ranges — that's the whole point of the per-tenant isolation
		// test for the overlap detector.
		seedYearAndPeriods(tenantA);
		seedYearAndPeriods(tenantB);

		return new Fixture(tenantA, tenantB);
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

	private void seedYearAndPeriods(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicYear year = new AcademicYear();
			year.setName("2026");
			year.setStartDate(LocalDate.parse("2026-03-01"));
			year.setEndDate(LocalDate.parse("2026-12-15"));
			year.setStatus(AcademicYearStatus.ACTIVE);
			AcademicYear savedYear = yearRepository.saveAndFlush(year);

			AcademicPeriod p1 = new AcademicPeriod();
			p1.setAcademicYear(savedYear);
			p1.setPeriodType(PeriodType.BIMESTRE);
			p1.setOrdinal(1);
			p1.setName("I Bimestre");
			p1.setStartDate(LocalDate.parse("2026-03-01"));
			p1.setEndDate(LocalDate.parse("2026-05-15"));
			periodRepository.save(p1);

			AcademicPeriod p2 = new AcademicPeriod();
			p2.setAcademicYear(savedYear);
			p2.setPeriodType(PeriodType.BIMESTRE);
			p2.setOrdinal(2);
			p2.setName("II Bimestre");
			p2.setStartDate(LocalDate.parse("2026-05-16"));
			p2.setEndDate(LocalDate.parse("2026-07-31"));
			periodRepository.save(p2);

			periodRepository.flush();
			return savedYear;
		}));
	}
}
