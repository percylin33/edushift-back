package com.edushift.modules.academic.competency;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
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
 * Cross-tenant isolation IT for {@code /v1/academic/courses/{courseUuid}/competencies}
 * and friends (Sprint 5A / BE-5A.2).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A only sees A's competencies;
 *       B's never appear in the listing.</li>
 *   <li><strong>Cross-tenant access</strong> — GET / DELETE on B's
 *       competency from A → 404 (anti-enumeration).</li>
 *   <li><strong>Code uniqueness is per-course</strong> — both tenants can
 *       have the same {@code MAT_C1} code inside their own MAT course.</li>
 *   <li><strong>Cross-tenant course reference</strong> — POST referencing
 *       B's course UUID from A → 404.</li>
 *   <li><strong>Capacity isolation</strong> — capacity created in A's
 *       competency is invisible to B (cross-tenant GET → 404).</li>
 *   <li><strong>Seed isolation</strong> — POST seed-defaults on A's MAT
 *       course doesn't seed anything on B's MAT course.</li>
 * </ul>
 */
@DisplayName("Competencies multi-tenancy isolation")
class CompetencyTenantIsolationIT extends IntegrationTest {

	private static final String COURSE_BASE = "/v1/academic/courses";
	private static final String COMPETENCY_BASE = "/v1/academic/competencies";
	private static final String CAPACITY_BASE = "/v1/academic/capacities";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-comp@isolation.test";
	private static final String PASSWORD_A = "PassCompA-1!";
	private static final String PASSWORD_B = "PassCompB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private CourseLevelRepository courseLevelRepository;
	@Autowired private CompetencyRepository competencyRepository;
	@Autowired private CapacityRepository capacityRepository;
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
		@DisplayName("admin A only sees A's competencies in the course listing")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					COURSE_BASE + "/" + fixture.courseA().getPublicUuid() + "/competencies",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<Competency> bs = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> competencyRepository.findAll()));
			List<UUID> bIds = bs.stream().map(Competency::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B competency publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's competency by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			Competency anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> competencyRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					COMPETENCY_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A reading B's capacity by id → 404")
		void crossTenantCapacityIs404() throws Exception {
			Fixture fixture = setupTenants();

			Capacity anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> capacityRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					CAPACITY_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A listing competencies of B's course → 404")
		void crossTenantCourseIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					COURSE_BASE + "/" + fixture.courseB().getPublicUuid() + "/competencies",
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
		@DisplayName("Same competency CODE allowed in two tenants (per-course uniqueness)")
		void codeUniquenessIsPerCourse() {
			Fixture fixture = setupTenants();

			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> competencyRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> competencyRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant course reference on POST → 404 RESOURCE_NOT_FOUND")
		void crossTenantPostCourseIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"code\":\"MAT_HACK\",\"name\":\"Hacker\"}";

			ResponseEntity<String> response = doPost(
					COURSE_BASE + "/" + fixture.courseB().getPublicUuid() + "/competencies",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE on competency → 404")
		void crossTenantDeleteCompetencyIs404() throws Exception {
			Fixture fixture = setupTenants();

			Competency anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> competencyRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					COMPETENCY_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE on capacity → 404")
		void crossTenantDeleteCapacityIs404() throws Exception {
			Fixture fixture = setupTenants();

			Capacity anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> capacityRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					CAPACITY_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Seed-defaults on A's MAT course doesn't seed B's MAT course")
		void seedDoesNotCrossTenant() throws Exception {
			Fixture fixture = setupTenantsEmpty();

			long bBefore = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> competencyRepository.count()));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doPost(
					COURSE_BASE + "/" + fixture.courseA().getPublicUuid()
							+ "/competencies/seed-defaults",
					loginA.accessToken(), "");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			long aAfter = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> competencyRepository.count()));
			long bAfter = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> competencyRepository.count()));

			assertThat(aAfter).as("A's competencies after seed").isGreaterThan(0);
			assertThat(bAfter).as("B's competencies untouched").isEqualTo(bBefore);
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

	record Fixture(Tenant tenantA, Tenant tenantB, Course courseA, Course courseB) {}

	/**
	 * Creates two tenants with one MAT course each + 2 competencies and
	 * 1 capacity per competency (using identical codes across tenants
	 * to prove per-course uniqueness).
	 */
	private Fixture setupTenants() {
		Fixture base = setupTenantsEmpty();

		seedCompetency(base.tenantA(), base.courseA(), "MAT_C1", 1, "MAT_C1_CAP1");
		seedCompetency(base.tenantA(), base.courseA(), "MAT_C2", 2, "MAT_C2_CAP1");
		seedCompetency(base.tenantB(), base.courseB(), "MAT_C1", 1, "MAT_C1_CAP1");
		seedCompetency(base.tenantB(), base.courseB(), "MAT_C2", 2, "MAT_C2_CAP1");

		return base;
	}

	/**
	 * Creates two tenants + courses but no competencies. Used by the
	 * seed-defaults isolation test that needs a clean slate on tenant A.
	 */
	private Fixture setupTenantsEmpty() {
		Tenant tenantA = createTenant("it-comp-a-");
		Tenant tenantB = createTenant("it-comp-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		Course courseA = seedCourse(tenantA, "MAT", "Matemática");
		Course courseB = seedCourse(tenantB, "MAT", "Matemática");

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

	private void seedCompetency(Tenant tenant, Course course, String code,
			int displayOrder, String capacityCode) {
		TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Competency competency = new Competency();
			competency.setCourse(course);
			competency.setCode(code);
			competency.setName("Name " + code);
			competency.setDisplayOrder(displayOrder);
			competency.setIsActive(true);
			Competency persisted = competencyRepository.saveAndFlush(competency);

			Capacity capacity = new Capacity();
			capacity.setCompetency(persisted);
			capacity.setCode(capacityCode);
			capacity.setName("Name " + capacityCode);
			capacity.setDisplayOrder(1);
			capacity.setIsActive(true);
			capacityRepository.saveAndFlush(capacity);
			return persisted;
		}));
	}
}
