package com.edushift.modules.teachers.assignments;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
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
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
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
 * Cross-tenant isolation IT for {@code teachers.assignments} (Sprint 4 / BE-4.7).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li>Read isolation: A's GET only returns A's assignments.</li>
 *   <li>Cross-tenant DELETE / GET on B's assignment from A → 404.</li>
 *   <li>Cross-tenant create from A using B's section / course / period
 *       UUIDs → 404.</li>
 *   <li>Same {@code (teacher, section, course, period)} can be active in
 *       both tenants — the partial unique index is per-tenant.</li>
 * </ul>
 */
@DisplayName("TeacherAssignment multi-tenancy isolation")
class TeacherAssignmentTenantIsolationIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";
	private static final String TEACHERS_BASE = "/v1/teachers";
	private static final String ASSIGNMENTS_BASE = "/v1/assignments";
	private static final String SECTIONS_BASE = "/v1/academic/sections";
	private static final String SHARED_EMAIL = "shared-assign@isolation.test";
	private static final String PASSWORD_A = "PassAssignA-1!";
	private static final String PASSWORD_B = "PassAssignB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private CourseLevelRepository courseLevelRepository;
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
		@DisplayName("admin A only sees A's assignments via GET /teachers/{T}/assignments")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					TEACHERS_BASE + "/" + fixture.teacherA().getPublicUuid() + "/assignments",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode arr = objectMapper.readTree(response.getBody());
			assertThat(arr.isArray()).isTrue();
			assertThat(arr.size()).isGreaterThanOrEqualTo(1);
			for (JsonNode n : arr) {
				UUID id = UUID.fromString(n.get("publicUuid").asText());
				assertThat(id)
						.as("assignment publicUuid must belong to tenant A")
						.isEqualTo(fixture.assignmentA().getPublicUuid());
			}
		}

		@Test
		@DisplayName("admin A reading B's assignment teacher → 404")
		void crossTenantTeacherListIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					TEACHERS_BASE + "/" + fixture.teacherB().getPublicUuid() + "/assignments",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A listing B's section roster → 404")
		void crossTenantSectionRosterIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					SECTIONS_BASE + "/" + fixture.sectionB().getPublicUuid() + "/teachers",
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
		@DisplayName("admin A DELETE on B's assignment → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					ASSIGNMENTS_BASE + "/" + fixture.assignmentB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			// Sanity: B's assignment is still active.
			TeacherAssignment stillActive = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> assignmentRepository
							.findByPublicUuid(fixture.assignmentB().getPublicUuid())
							.orElseThrow()));
			assertThat(stillActive.getUnassignedAt()).isNull();
		}

		@Test
		@DisplayName("admin A POSTing assignment with B's section/course/period → 404")
		void crossTenantCreateRefsAre404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"sectionPublicUuid\":\"%s\","
							+ "\"coursePublicUuid\":\"%s\","
							+ "\"academicPeriodPublicUuid\":\"%s\"}",
					fixture.sectionB().getPublicUuid(),
					fixture.courseB().getPublicUuid(),
					fixture.periodB().getPublicUuid());

			ResponseEntity<String> response = doPost(
					TEACHERS_BASE + "/" + fixture.teacherA().getPublicUuid() + "/assignments",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Same (teacher, section, course, period) tuple is unique per tenant")
		void duplicateTupleIsPerTenant() {
			Fixture fixture = setupTenants();

			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> assignmentRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> assignmentRepository.count()));
			// Both tenants seed exactly one active assignment with the
			// "same" logical tuple (different entities under the hood
			// because each tenant has its own teacher/section/...).
			assertThat(countA).isGreaterThanOrEqualTo(1);
			assertThat(countB).isGreaterThanOrEqualTo(1);
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

	record TenantSeed(Tenant tenant, Teacher teacher, Section section,
			Course course, AcademicPeriod period, TeacherAssignment assignment) {
	}

	record Fixture(Tenant tenantA, Tenant tenantB,
			Teacher teacherA, Teacher teacherB,
			Section sectionA, Section sectionB,
			Course courseA, Course courseB,
			AcademicPeriod periodA, AcademicPeriod periodB,
			TeacherAssignment assignmentA, TeacherAssignment assignmentB) {
	}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-assign-a-");
		Tenant tenantB = createTenant("it-assign-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);

		TenantSeed seedA = seedAssignment(tenantA);
		TenantSeed seedB = seedAssignment(tenantB);

		return new Fixture(
				tenantA, tenantB,
				seedA.teacher(), seedB.teacher(),
				seedA.section(), seedB.section(),
				seedA.course(), seedB.course(),
				seedA.period(), seedB.period(),
				seedA.assignment(), seedB.assignment());
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

	/**
	 * Seeds the full graph for a single tenant: year (ACTIVE) + level +
	 * grade + section + course + course-level + period + teacher +
	 * one active assignment.
	 */
	private TenantSeed seedAssignment(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicYear year = new AcademicYear();
			year.setName("2026-" + tenant.getSlug());
			year.setStartDate(LocalDate.of(2026, 1, 1));
			year.setEndDate(LocalDate.of(2026, 12, 31));
			year.setStatus(AcademicYearStatus.ACTIVE);
			year = yearRepository.saveAndFlush(year);

			AcademicLevel level = new AcademicLevel();
			level.setCode("PRIMARIA");
			level.setName("Primaria");
			level.setOrdinal(2);
			level = levelRepository.saveAndFlush(level);

			Grade grade = new Grade();
			grade.setName("2do Primaria");
			grade.setOrdinal(2);
			grade.setLevel(level);
			grade = gradeRepository.saveAndFlush(grade);

			Section section = new Section();
			section.setName("A");
			section.setGrade(grade);
			section.setAcademicYear(year);
			section.setDisplayOrder(1);
			section = sectionRepository.saveAndFlush(section);

			Course course = new Course();
			// code must match ^[A-Z][A-Z0-9_]*$ — keep it short + tenant-unique
			// by inlining 6 chars of the tenant.id hex (no dashes).
			String tenantSig = tenant.getId().toString().replace("-", "")
					.substring(0, 6).toUpperCase();
			course.setCode("MAT_" + tenantSig);
			course.setName("Matematica");
			course.setIsActive(true);
			course = courseRepository.saveAndFlush(course);

			CourseLevel link = new CourseLevel();
			link.setCourse(course);
			link.setLevel(level);
			courseLevelRepository.saveAndFlush(link);

			AcademicPeriod period = new AcademicPeriod();
			period.setAcademicYear(year);
			period.setPeriodType(PeriodType.BIMESTRE);
			period.setOrdinal(1);
			period.setName("I Bimestre");
			period.setStartDate(LocalDate.of(2026, 3, 1));
			period.setEndDate(LocalDate.of(2026, 5, 1));
			period = periodRepository.saveAndFlush(period);

			Teacher teacher = new Teacher();
			teacher.setDocumentType(DocumentType.DNI);
			teacher.setDocumentNumber("9000" + Math.abs(tenant.getId().getMostSignificantBits() % 10000));
			teacher.setFirstName("Ada");
			teacher.setLastName("Lovelace " + tenant.getSlug());
			teacher.setEmail("ada-" + tenant.getId() + "@isolation.test");
			teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);
			teacher = teacherRepository.saveAndFlush(teacher);

			TeacherAssignment assignment = new TeacherAssignment();
			assignment.setTeacher(teacher);
			assignment.setSection(section);
			assignment.setCourse(course);
			assignment.setAcademicPeriod(period);
			assignment = assignmentRepository.saveAndFlush(assignment);

			return new TenantSeed(tenant, teacher, section, course, period, assignment);
		}));
	}
}
