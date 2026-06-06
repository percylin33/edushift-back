package com.edushift.modules.academic.section;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
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
 * Cross-tenant isolation IT for {@code /v1/academic/sections}
 * (Sprint 4 / BE-4.3).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A's GET only sees A's
 *       sections; B's never appear.</li>
 *   <li><strong>Cross-tenant GET / PUT / DELETE</strong> by id → 404
 *       (anti-enumeration).</li>
 *   <li><strong>Same name allowed in two tenants</strong> — uniqueness
 *       is per-tenant.</li>
 *   <li><strong>Race-condition fallback</strong> — duplicate name in
 *       same {@code (year, grade)} → 409 SECTION_NAME_TAKEN.</li>
 *   <li><strong>Year lock</strong> — create on a CLOSED year → 409
 *       ACADEMIC_YEAR_LOCKED.</li>
 * </ul>
 */
@DisplayName("Sections multi-tenancy isolation")
class SectionTenantIsolationIT extends IntegrationTest {

	private static final String SECTIONS_BASE = "/v1/academic/sections";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-section@isolation.test";
	private static final String PASSWORD_A = "PassSecA-1!";
	private static final String PASSWORD_B = "PassSecB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private AcademicSeedService seedService;
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
		@DisplayName("admin A only sees A's sections")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(SECTIONS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<Section> bSections = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> sectionRepository.findAll()));
			List<UUID> bIds = bSections.stream().map(Section::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B section publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's section by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			Section anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> sectionRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					SECTIONS_BASE + "/" + anyOfB.getPublicUuid(),
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
		@DisplayName("Same section name allowed across two tenants")
		void sameNameAllowedInTwoTenants() throws Exception {
			Fixture fixture = setupTenants();

			// Both tenants seeded with sections "A" already (via setupTenants).
			// Verify both rows coexist.
			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> sectionRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> sectionRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant PUT → 404")
		void crossTenantPutIs404() throws Exception {
			Fixture fixture = setupTenants();

			Section anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> sectionRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doPut(
					SECTIONS_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken(),
					"{\"name\":\"hacked\"}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			Section anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> sectionRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					SECTIONS_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Race condition: same name in same (year, grade) → 409 SECTION_NAME_TAKEN")
		void duplicateNameRejected() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"academicYearPublicUuid\":\"%s\",\"gradePublicUuid\":\"%s\",\"name\":\"A\"}",
					fixture.yearA().getPublicUuid(),
					fixture.primariaFirstGradeA().getPublicUuid());

			ResponseEntity<String> response = doPost(SECTIONS_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
			assertThat(errors.get(0).get("code").asText()).isEqualTo("SECTION_NAME_TAKEN");
		}

		@Test
		@DisplayName("CLOSED year → 409 ACADEMIC_YEAR_LOCKED on create")
		void closedYearRejected() throws Exception {
			Fixture fixture = setupTenants();

			// Close year of tenant A
			TenantContext.runAs(fixture.tenantA().getId(), () -> tx().execute(s -> {
				AcademicYear year = yearRepository.findByPublicUuid(
						fixture.yearA().getPublicUuid()).orElseThrow();
				year.setStatus(AcademicYearStatus.CLOSED);
				return yearRepository.save(year);
			}));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"academicYearPublicUuid\":\"%s\",\"gradePublicUuid\":\"%s\",\"name\":\"Z\"}",
					fixture.yearA().getPublicUuid(),
					fixture.primariaFirstGradeA().getPublicUuid());

			ResponseEntity<String> response = doPost(SECTIONS_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
			assertThat(errors.get(0).get("code").asText()).isEqualTo("ACADEMIC_YEAR_LOCKED");
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

	// =========================================================================
	// Fixtures
	// =========================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			AcademicYear yearA, AcademicYear yearB,
			Grade primariaFirstGradeA, Grade primariaFirstGradeB
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-sec-a-");
		Tenant tenantB = createTenant("it-sec-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		AcademicYear yearA = activateNewYear(tenantA, "2026");
		AcademicYear yearB = activateNewYear(tenantB, "2026");

		Grade firstA = firstGradeOfPrimaria(tenantA);
		Grade firstB = firstGradeOfPrimaria(tenantB);

		// Each tenant has at least one Section "A" so list-isolation tests
		// have something to assert on.
		createSection(tenantA, yearA, firstA, "A");
		createSection(tenantB, yearB, firstB, "A");

		return new Fixture(tenantA, tenantB, yearA, yearB, firstA, firstB);
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

	private void seedAcademicCatalog(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					seedService.seedDefaults(tenant.getId());
					return null;
				}));
	}

	private AcademicYear activateNewYear(Tenant tenant, String name) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicYear y = new AcademicYear();
			y.setName(name);
			y.setStatus(AcademicYearStatus.ACTIVE);
			y.setStartDate(LocalDate.of(2026, 3, 1));
			y.setEndDate(LocalDate.of(2026, 12, 15));
			return yearRepository.saveAndFlush(y);
		}));
	}

	private Grade firstGradeOfPrimaria(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			return gradeRepository.findAllByLevelOrderByOrdinalAsc(primaria).get(0);
		}));
	}

	private void createSection(Tenant tenant, AcademicYear year, Grade grade, String name) {
		TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Section section = new Section();
			section.setAcademicYear(year);
			section.setGrade(grade);
			section.setName(name);
			section.setDisplayOrder(1);
			return sectionRepository.saveAndFlush(section);
		}));
	}
}
