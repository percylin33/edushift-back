package com.edushift.modules.quizzes;

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
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Cross-tenant isolation IT for {@code /api/v1/.../quizzes} (Sprint 7b / BE-7b.1).
 *
 * <p>Validates that LMS quizzes (BE-7b.0 + BE-7b.1) inherit the same
 * tenant isolation guarantees as the rest of the platform: a user
 * authenticated in tenant B must never be able to read, mutate,
 * delete, list, publish or close a quiz owned by tenant A. Because
 * the module relies on Hibernate's {@code @TenantId} auto-filter
 * (no path-supplied tenant ids), the expected outcome of every
 * cross-tenant request is HTTP 404 — never 403, which would leak the
 * existence of the resource (anti-enumeration contract).
 *
 * <p>Mirrors {@code MaterialTenantIsolationIT} (Sprint 7a / BE-7a.4)
 * and {@code TaskTenantIsolationIT} (Sprint 7a / BE-7a.4) so future
 * maintainers have a single template to copy from.
 */
@DisplayName("Quizzes multi-tenancy isolation")
class QuizTenantIsolationIT extends IntegrationTest {

	private static final String QUIZZES_BASE = "/v1/quizzes";
	private static final String SECTIONS_QUIZZES_BASE = "/v1/sections/{section}/quizzes";
	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "admin@quiz-isolation.test";
	private static final String PASSWORD_A = "PassQuizA-1!";
	private static final String PASSWORD_B = "PassQuizB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private QuizRepository quizRepository;
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
	// Cross-tenant reads
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant reads (admin B looking at admin A's quiz)")
	class CrossTenantReads {

		@Test
		@DisplayName("GET /quizzes/{A's uuid} → 404")
		void getByPublicUuidReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Quiz quizA = createQuizInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doGet(
					QUIZZES_BASE + "/" + quizA.getPublicUuid(),
					loginB.accessToken());

			assertThat(response.getStatusCode())
					.as("cross-tenant GET on a quiz must be 404 (anti-enumeration)")
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

	@Test
	@DisplayName("GET /sections/{A's section}/quizzes as admin B → 404 (anti-enumeration; "
			+ "consistent with the rest of the quiz endpoints)")
	void listBySectionDoesNotLeak() throws Exception {
		Fixture fixture = setupTenants();
		Quiz quizA = createQuizInA(fixture);

		// Seed a quiz in B as well so the lookup has neighbours; the assertion
		// is on the status code (404 anti-enumeration), not on payload contents.
		createQuizInB(fixture);

		AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
		ResponseEntity<String> response = doGet(
				SECTIONS_QUIZZES_BASE.replace("{section}",
						fixture.sectionA().getPublicUuid().toString()),
				loginB.accessToken());

		assertThat(response.getStatusCode())
				.as("cross-tenant GET on a section's quizzes must be 404 (anti-enumeration)")
				.isEqualTo(HttpStatus.NOT_FOUND);
	}
	}

	// =========================================================================
	// Cross-tenant writes
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant writes (admin B trying to mutate admin A's quiz)")
	class CrossTenantWrites {

		@Test
		@DisplayName("POST /sections/{A's section}/quizzes as admin B → 404")
		void createOnAnotherTenantsSectionReturns404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			String body = String.format(
					"{\"title\":\"hijacked\",\"description\":\"hijack\","
							+ "\"maxScore\":20,\"maxAttempts\":1}");

			ResponseEntity<String> response = doPost(
					SECTIONS_QUIZZES_BASE.replace("{section}",
							fixture.sectionA().getPublicUuid().toString()),
					loginB.accessToken(),
					body);

			assertThat(response.getStatusCode())
					.as("admin B cannot create a quiz in A's section; the section is invisible")
					.isEqualTo(HttpStatus.NOT_FOUND);

			// Defence-in-depth: confirm no row sneaked in under tenant A.
			List<Quiz> aQuizzes = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> quizRepository.findAll()));
			assertThat(aQuizzes).isEmpty();
		}

		@Test
		@DisplayName("PATCH /quizzes/{A's uuid} as admin B → 404")
		void patchReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Quiz quizA = createQuizInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			String body = "{\"title\":\"hijacked\"}";

			ResponseEntity<String> response = doPatch(
					QUIZZES_BASE + "/" + quizA.getPublicUuid(),
					loginB.accessToken(),
					body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("DELETE /quizzes/{A's uuid} as admin B → 404")
		void deleteReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Quiz quizA = createQuizInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doDelete(
					QUIZZES_BASE + "/" + quizA.getPublicUuid(),
					loginB.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			// Defence-in-depth: the quiz is still alive in A.
			Quiz stillAlive = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s ->
							quizRepository.findByPublicUuid(quizA.getPublicUuid()).orElseThrow()));
			assertThat(stillAlive.isDeleted()).isFalse();
		}
	}

	// =========================================================================
	// Cross-tenant lifecycle (publish / close)
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant lifecycle (publish / close) transitions on A's quiz")
	class CrossTenantLifecycle {

		@Test
		@DisplayName("POST /quizzes/{A's uuid}/publish as admin B → 404, status stays DRAFT")
		void publishOnAnotherTenantsQuizReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Quiz quizA = createQuizInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doPost(
					QUIZZES_BASE + "/" + quizA.getPublicUuid() + "/publish",
					loginB.accessToken(),
					"");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			// Defence-in-depth: the quiz is still DRAFT in A.
			Quiz stillDraft = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s ->
							quizRepository.findByPublicUuid(quizA.getPublicUuid()).orElseThrow()));
			assertThat(stillDraft.getStatus().name()).isEqualTo("DRAFT");
			assertThat(stillDraft.getPublishedAt()).isNull();
		}

		@Test
		@DisplayName("POST /quizzes/{A's uuid}/close as admin B → 404, status stays DRAFT")
		void closeOnAnotherTenantsQuizReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Quiz quizA = createQuizInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doPost(
					QUIZZES_BASE + "/" + quizA.getPublicUuid() + "/close",
					loginB.accessToken(),
					"");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			// Defence-in-depth: the quiz is still DRAFT in A.
			Quiz stillDraft = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s ->
							quizRepository.findByPublicUuid(quizA.getPublicUuid()).orElseThrow()));
			assertThat(stillDraft.getStatus().name()).isEqualTo("DRAFT");
			assertThat(stillDraft.getClosedAt()).isNull();
		}
	}

	// =========================================================================
	// Same-tenant happy path (control)
	// =========================================================================

	@Nested
	@DisplayName("Same-tenant happy path (control)")
	class SameTenantAccess {

		@Test
		@DisplayName("Admin A creates then GETs a quiz in A's section → 201 then 200")
		void createThenGet() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"title\":\"sumative quiz\",\"maxScore\":20,\"maxAttempts\":1}";
			ResponseEntity<String> createResponse = doPost(
					SECTIONS_QUIZZES_BASE.replace("{section}",
							fixture.sectionA().getPublicUuid().toString()),
					loginA.accessToken(),
					body);

			assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			JsonNode created = objectMapper.readTree(createResponse.getBody());
			String quizUuid = created.get("data").get("publicUuid").asText();

			ResponseEntity<String> getResponse = doGet(
					QUIZZES_BASE + "/" + quizUuid,
					loginA.accessToken());
			assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode got = objectMapper.readTree(getResponse.getBody());
			assertThat(got.get("success").asBoolean()).isTrue();
			assertThat(got.get("data").get("publicUuid").asText()).isEqualTo(quizUuid);
			assertThat(got.get("data").get("status").asText()).isEqualTo("DRAFT");
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

	private ResponseEntity<String> doPatch(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PATCH,
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
			User adminA, User adminB,
			Section sectionA, Section sectionB
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-quiz-a-");
		Tenant tenantB = createTenant("it-quiz-b-");
		User adminA = createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		User adminB = createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		AcademicYear yearA = activateNewYear(tenantA, "2026");
		AcademicYear yearB = activateNewYear(tenantB, "2026");

		Grade firstA = firstGradeOfPrimaria(tenantA);
		Grade firstB = firstGradeOfPrimaria(tenantB);

		Section sectionA = createSection(tenantA, yearA, firstA, "A");
		Section sectionB = createSection(tenantB, yearB, firstB, "A");

		return new Fixture(tenantA, tenantB, adminA, adminB, sectionA, sectionB);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
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

	private Section createSection(Tenant tenant, AcademicYear year, Grade grade, String name) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Section section = new Section();
			section.setAcademicYear(year);
			section.setGrade(grade);
			section.setName(name);
			section.setDisplayOrder(1);
			return sectionRepository.saveAndFlush(section);
		}));
	}

	private Quiz createQuizInA(Fixture fixture) {
		return createQuiz(fixture.tenantA(), fixture.sectionA(), fixture.adminA(),
				"quiz-A-" + UUID.randomUUID().toString().substring(0, 8));
	}

	private Quiz createQuizInB(Fixture fixture) {
		return createQuiz(fixture.tenantB(), fixture.sectionB(), fixture.adminB(),
				"quiz-B-" + UUID.randomUUID().toString().substring(0, 8));
	}

	private Quiz createQuiz(Tenant tenant, Section section, User owner, String title) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Quiz q = new Quiz();
			q.setSection(section);
			q.setTitle(title);
			q.setDescription("description for " + title);
			q.setDueAt(Instant.now().plus(7, ChronoUnit.DAYS));
			q.setMaxScore((short) 20);
			q.setAttemptsAllowed((short) 1);
			q.setOwnerUserId(owner.getPublicUuid());
			// status defaults to DRAFT via @PrePersist.
			return quizRepository.saveAndFlush(q);
		}));
	}
}
