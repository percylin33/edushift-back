package com.edushift.modules.academic.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
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
 * Cross-tenant isolation IT for {@code /v1/academic/courses}
 * (Sprint 4 / BE-4.4).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A only sees A's courses;
 *       B's never appear.</li>
 *   <li><strong>Cross-tenant access</strong> — GET / DELETE on B's
 *       course from A → 404.</li>
 *   <li><strong>Code uniqueness is per-tenant</strong> — both tenants
 *       can have a course "MAT".</li>
 *   <li><strong>Cross-tenant level reference</strong> — POST referencing
 *       B's level UUID from A → 404 (anti-enumeration).</li>
 *   <li><strong>Replace-levels diff</strong> — atomically applies on the
 *       owning tenant without touching the other.</li>
 * </ul>
 */
@DisplayName("Courses multi-tenancy isolation")
class CourseTenantIsolationIT extends IntegrationTest {

	private static final String COURSES_BASE = "/v1/academic/courses";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-course@isolation.test";
	private static final String PASSWORD_A = "PassCourseA-1!";
	private static final String PASSWORD_B = "PassCourseB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private CourseLevelRepository courseLevelRepository;
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
		@DisplayName("admin A only sees A's courses")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(COURSES_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<Course> bCourses = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> courseRepository.findAll()));
			List<UUID> bIds = bCourses.stream().map(Course::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B course publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's course by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			Course anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> courseRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					COURSES_BASE + "/" + anyOfB.getPublicUuid(),
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
		@DisplayName("Same course CODE is allowed in two tenants")
		void codeUniquenessIsPerTenant() {
			Fixture fixture = setupTenants();

			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> courseRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> courseRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant level reference → 404 RESOURCE_NOT_FOUND")
		void crossTenantLevelIs404() throws Exception {
			Fixture fixture = setupTenants();

			AcademicLevel levelOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> levelRepository.findByCodeIgnoreCase("PRIMARIA")
							.orElseThrow()));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"code\":\"NEWCOURSE\",\"name\":\"Test\",\"levelPublicUuids\":[\"%s\"]}",
					levelOfB.getPublicUuid());

			ResponseEntity<String> response = doPost(COURSES_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			Course anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> courseRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					COURSES_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Replace levels in A doesn't touch B's course_levels rows")
		void replaceDoesNotCascade() throws Exception {
			Fixture fixture = setupTenants();

			// Snapshot B's pivot count before
			long bCountBefore = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> courseLevelRepository.count()));

			// Pick A's MAT and replace its levels with INICIAL only
			Course matA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> courseRepository.findByCodeIgnoreCase("MAT")
							.orElseThrow()));
			AcademicLevel inicialA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> levelRepository.findByCodeIgnoreCase("INICIAL")
							.orElseThrow()));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"levelPublicUuids\":[\"%s\"]}",
					inicialA.getPublicUuid());

			ResponseEntity<String> response = doPost(
					COURSES_BASE + "/" + matA.getPublicUuid() + "/levels",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			long bCountAfter = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> courseLevelRepository.count()));

			assertThat(bCountAfter).isEqualTo(bCountBefore);
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
		Tenant tenantA = createTenant("it-course-a-");
		Tenant tenantB = createTenant("it-course-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		// Each tenant gets a "MAT" course linked to PRIMARIA + SECUNDARIA
		seedCourse(tenantA, "MAT", "Matemática");
		seedCourse(tenantB, "MAT", "Matemática");

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

	private void seedAcademicCatalog(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					seedService.seedDefaults(tenant.getId());
					return null;
				}));
	}

	private void seedCourse(Tenant tenant, String code, String name) {
		TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			AcademicLevel secundaria = levelRepository.findByCodeIgnoreCase("SECUNDARIA")
					.orElseThrow();
			Course course = new Course();
			course.setCode(code);
			course.setName(name);
			course.setIsActive(true);
			Course saved = courseRepository.saveAndFlush(course);

			CourseLevel link1 = new CourseLevel();
			link1.setCourse(saved);
			link1.setLevel(primaria);
			courseLevelRepository.save(link1);

			CourseLevel link2 = new CourseLevel();
			link2.setCourse(saved);
			link2.setLevel(secundaria);
			courseLevelRepository.save(link2);

			courseLevelRepository.flush();
			return saved;
		}));
	}
}
