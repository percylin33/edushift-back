package com.edushift.modules.academic.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
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
 * Cross-tenant isolation IT for {@code /v1/academic/courses/{courseUuid}/units}
 * and {@code /v1/academic/units/{publicUuid}} (Sprint 5A / BE-5A.1).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A only sees A's units;
 *       B's never appear in the listing.</li>
 *   <li><strong>Cross-tenant access</strong> — GET / DELETE on B's
 *       unit from A → 404.</li>
 *   <li><strong>Name uniqueness is per-course</strong> — both tenants
 *       can have a unit "Unidad I" inside their own MAT course (and the
 *       same name can also be reused in a sibling course inside one
 *       tenant).</li>
 *   <li><strong>Cross-tenant course reference</strong> — POST referencing
 *       B's course UUID from A → 404 (anti-enumeration).</li>
 *   <li><strong>Reorder isolation</strong> — reorder in A doesn't shift
 *       B's display_order values.</li>
 * </ul>
 */
@DisplayName("Units multi-tenancy isolation")
class UnitTenantIsolationIT extends IntegrationTest {

	private static final String UNITS_BY_COURSE_BASE = "/v1/academic/courses";
	private static final String UNITS_FLAT_BASE = "/v1/academic/units";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-unit@isolation.test";
	private static final String PASSWORD_A = "PassUnitA-1!";
	private static final String PASSWORD_B = "PassUnitB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private CourseLevelRepository courseLevelRepository;
	@Autowired private UnitRepository unitRepository;
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
		@DisplayName("admin A only sees A's units in the course listing")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					UNITS_BY_COURSE_BASE + "/" + fixture.courseA().getPublicUuid() + "/units",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<Unit> bUnits = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> unitRepository.findAll()));
			List<UUID> bIds = bUnits.stream().map(Unit::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B unit publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's unit by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			Unit anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> unitRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					UNITS_FLAT_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A listing units of B's course → 404 RESOURCE_NOT_FOUND")
		void crossTenantCourseIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					UNITS_BY_COURSE_BASE + "/" + fixture.courseB().getPublicUuid() + "/units",
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
		@DisplayName("Same unit NAME is allowed in two tenants (per-course uniqueness)")
		void nameUniquenessIsPerCourse() {
			Fixture fixture = setupTenants();

			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> unitRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> unitRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant course reference on POST → 404 RESOURCE_NOT_FOUND")
		void crossTenantPostCourseIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"name\":\"Unidad Hacker\",\"displayOrder\":99}";

			ResponseEntity<String> response = doPost(
					UNITS_BY_COURSE_BASE + "/" + fixture.courseB().getPublicUuid() + "/units",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			Unit anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> unitRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					UNITS_FLAT_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Reorder in A doesn't shift B's display_order values")
		void reorderDoesNotCrossTenant() throws Exception {
			Fixture fixture = setupTenants();

			// Snapshot B's display orders before
			List<Unit> bBefore = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> unitRepository.findAllByCourseOrderByDisplayOrderAsc(
							fixture.courseB())));
			List<Integer> bOrdersBefore = bBefore.stream()
					.map(Unit::getDisplayOrder).toList();

			// Reorder A's units (swap positions 1 and 2)
			List<Unit> aUnits = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> unitRepository.findAllByCourseOrderByDisplayOrderAsc(
							fixture.courseA())));
			Unit u1A = aUnits.get(0);
			Unit u2A = aUnits.get(1);

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"items\":[{\"publicUuid\":\"%s\",\"displayOrder\":2},{\"publicUuid\":\"%s\",\"displayOrder\":1}]}",
					u1A.getPublicUuid(), u2A.getPublicUuid());

			ResponseEntity<String> response = rest.exchange(
					UNITS_BY_COURSE_BASE + "/" + fixture.courseA().getPublicUuid()
							+ "/units/reorder",
					HttpMethod.PATCH,
					new HttpEntity<>(body, jsonHeadersWithAuth(loginA.accessToken())),
					String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			// B's order is untouched
			List<Unit> bAfter = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> unitRepository.findAllByCourseOrderByDisplayOrderAsc(
							fixture.courseB())));
			List<Integer> bOrdersAfter = bAfter.stream()
					.map(Unit::getDisplayOrder).toList();

			assertThat(bOrdersAfter).isEqualTo(bOrdersBefore);
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
	// Fixtures
	// =========================================================================

	record Fixture(Tenant tenantA, Tenant tenantB, Course courseA, Course courseB) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-unit-a-");
		Tenant tenantB = createTenant("it-unit-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		Course courseA = seedCourse(tenantA, "MAT", "Matemática");
		Course courseB = seedCourse(tenantB, "MAT", "Matemática");

		// Each tenant's course gets 2 units with the same names — proves
		// per-course uniqueness instead of per-tenant.
		seedUnit(tenantA, courseA, "Unidad I", 1);
		seedUnit(tenantA, courseA, "Unidad II", 2);
		seedUnit(tenantB, courseB, "Unidad I", 1);
		seedUnit(tenantB, courseB, "Unidad II", 2);

		return new Fixture(tenantA, tenantB, courseA, courseB);
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

	private Course seedCourse(Tenant tenant, String code, String name) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			Course course = new Course();
			course.setCode(code);
			course.setName(name);
			course.setIsActive(true);
			Course saved = courseRepository.saveAndFlush(course);

			CourseLevel link = new CourseLevel();
			link.setCourse(saved);
			link.setLevel(primaria);
			courseLevelRepository.save(link);
			courseLevelRepository.flush();
			return saved;
		}));
	}

	private void seedUnit(Tenant tenant, Course course, String name, int displayOrder) {
		TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Unit unit = new Unit();
			unit.setCourse(course);
			unit.setName(name);
			unit.setDisplayOrder(displayOrder);
			unit.setIsActive(true);
			return unitRepository.saveAndFlush(unit);
		}));
	}
}
