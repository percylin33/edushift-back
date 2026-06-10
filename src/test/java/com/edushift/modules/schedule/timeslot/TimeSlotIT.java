package com.edushift.modules.schedule.timeslot;

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
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
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
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Combined IT for {@code /v1/teacher-assignments/{a}/time-slots},
 * {@code /v1/time-slots/{id}}, {@code /v1/teachers/{t}/schedule} and
 * {@code /v1/academic/sections/{s}/schedule} (Sprint 5A / BE-5A.3).
 *
 * <p>Two nested groups, sharing one fixture builder:</p>
 * <ul>
 *   <li><strong>Overlap</strong> — verifies the JPQL probe runs
 *       correctly against Postgres for boundary, partial-overlap and
 *       happy paths (mocks already cover the algorithm; this is a
 *       contract test).</li>
 *   <li><strong>TenantIsolation</strong> — cross-tenant 404s on every
 *       slot endpoint plus the two reverse views.</li>
 * </ul>
 */
@DisplayName("TimeSlot IT — overlap + multi-tenancy")
class TimeSlotIT extends IntegrationTest {

	private static final String SLOTS_BY_ASSIGNMENT_BASE = "/v1/teacher-assignments";
	private static final String SLOTS_FLAT_BASE = "/v1/time-slots";
	private static final String TEACHER_SCHEDULE_BASE = "/v1/teachers";
	private static final String SECTION_SCHEDULE_BASE = "/v1/academic/sections";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-slot@isolation.test";
	private static final String PASSWORD_A = "PassSlotA-1!";
	private static final String PASSWORD_B = "PassSlotB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicPeriodRepository periodRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private CourseLevelRepository courseLevelRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private TimeSlotRepository timeSlotRepository;
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
	// Overlap — JPQL contract on real Postgres
	// =========================================================================

	@Nested
	@DisplayName("Overlap detection")
	class Overlap {

		@Test
		@DisplayName("partial overlap on same assignment+day → 409 TIME_SLOT_OVERLAP")
		void partialOverlap() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			// Existing slot 08:00-09:00 already seeded in fixtureA.
			String body = "{\"dayOfWeek\":1,\"startTime\":\"08:30\",\"endTime\":\"09:30\"}";
			ResponseEntity<String> response = doPost(
					SLOTS_BY_ASSIGNMENT_BASE + "/"
							+ fixture.assignmentA().getPublicUuid() + "/time-slots",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody()).contains("TIME_SLOT_OVERLAP");
		}

		@Test
		@DisplayName("boundary case: end == other.start → no overlap, 201")
		void boundaryNotOverlap() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			// Existing slot 08:00-09:00; new slot starts exactly at 09:00.
			String body = "{\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}";
			ResponseEntity<String> response = doPost(
					SLOTS_BY_ASSIGNMENT_BASE + "/"
							+ fixture.assignmentA().getPublicUuid() + "/time-slots",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}

		@Test
		@DisplayName("different day on same assignment → no overlap, 201")
		void differentDay() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"dayOfWeek\":2,\"startTime\":\"08:00\",\"endTime\":\"09:00\"}";
			ResponseEntity<String> response = doPost(
					SLOTS_BY_ASSIGNMENT_BASE + "/"
							+ fixture.assignmentA().getPublicUuid() + "/time-slots",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}

		@Test
		@DisplayName("end <= start → 400 TIME_SLOT_DATE_INVERTED")
		void inverted() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"dayOfWeek\":3,\"startTime\":\"10:00\",\"endTime\":\"09:00\"}";
			ResponseEntity<String> response = doPost(
					SLOTS_BY_ASSIGNMENT_BASE + "/"
							+ fixture.assignmentA().getPublicUuid() + "/time-slots",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody()).contains("TIME_SLOT_DATE_INVERTED");
		}
	}

	// =========================================================================
	// Multi-tenancy isolation
	// =========================================================================

	@Nested
	@DisplayName("Multi-tenancy isolation")
	class TenantIsolation {

		@Test
		@DisplayName("admin A only sees A's slots in the assignment listing")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					SLOTS_BY_ASSIGNMENT_BASE + "/"
							+ fixture.assignmentA().getPublicUuid() + "/time-slots",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<TimeSlot> bSlots = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> timeSlotRepository.findAll()));
			List<UUID> bIds = bSlots.stream().map(TimeSlot::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds).as("tenant B slot leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's slot → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			TimeSlot anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> timeSlotRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					SLOTS_FLAT_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("cross-tenant assignment reference on POST → 404")
		void crossTenantPostAssignmentIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"dayOfWeek\":4,\"startTime\":\"08:00\",\"endTime\":\"09:00\"}";
			ResponseEntity<String> response = doPost(
					SLOTS_BY_ASSIGNMENT_BASE + "/"
							+ fixture.assignmentB().getPublicUuid() + "/time-slots",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			TimeSlot anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> timeSlotRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					SLOTS_FLAT_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("teacher schedule reverse view: cross-tenant teacher → 404")
		void crossTenantTeacherScheduleIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					TEACHER_SCHEDULE_BASE + "/" + fixture.teacherB().getPublicUuid()
							+ "/schedule",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("section schedule reverse view: cross-tenant section → 404")
		void crossTenantSectionScheduleIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					SECTION_SCHEDULE_BASE + "/" + fixture.sectionB().getPublicUuid()
							+ "/schedule",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("teacher schedule of A returns A's slots only")
		void teacherScheduleScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					TEACHER_SCHEDULE_BASE + "/" + fixture.teacherA().getPublicUuid()
							+ "/schedule",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<TimeSlot> bSlots = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> timeSlotRepository.findAll()));
			List<UUID> bIds = bSlots.stream().map(TimeSlot::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("slotPublicUuid").asText());
				assertThat(bIds).as("tenant B slot leaked into A's teacher schedule")
						.doesNotContain(id);
			}
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

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			Teacher teacherA, Teacher teacherB,
			Section sectionA, Section sectionB,
			TeacherAssignment assignmentA, TeacherAssignment assignmentB
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-slot-a-");
		Tenant tenantB = createTenant("it-slot-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		AssignmentBundle bundleA = seedAssignmentWithSlot(tenantA);
		AssignmentBundle bundleB = seedAssignmentWithSlot(tenantB);

		return new Fixture(tenantA, tenantB,
				bundleA.teacher(), bundleB.teacher(),
				bundleA.section(), bundleB.section(),
				bundleA.assignment(), bundleB.assignment());
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

	record AssignmentBundle(Teacher teacher, Section section,
			Course course, AcademicPeriod period,
			TeacherAssignment assignment) {}

	private AssignmentBundle seedAssignmentWithSlot(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			Grade grade = gradeRepository
					.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

			AcademicYear year = new AcademicYear();
			year.setName("2026-IT");
			year.setStartDate(LocalDate.of(2026, 3, 1));
			year.setEndDate(LocalDate.of(2026, 12, 20));
			year.setStatus(AcademicYearStatus.ACTIVE);
			AcademicYear savedYear = yearRepository.saveAndFlush(year);

			AcademicPeriod period = new AcademicPeriod();
			period.setAcademicYear(savedYear);
			period.setPeriodType(PeriodType.BIMESTRE);
			period.setOrdinal(1);
			period.setName("I Bimestre");
			period.setStartDate(LocalDate.of(2026, 3, 1));
			period.setEndDate(LocalDate.of(2026, 5, 31));
			AcademicPeriod savedPeriod = periodRepository.saveAndFlush(period);

			Section section = new Section();
			section.setAcademicYear(savedYear);
			section.setGrade(grade);
			section.setName("1ro A");
			Section savedSection = sectionRepository.saveAndFlush(section);

			Course course = new Course();
			course.setCode("MAT_IT");
			course.setName("Matemática IT");
			course.setIsActive(true);
			Course savedCourse = courseRepository.saveAndFlush(course);

			CourseLevel link = new CourseLevel();
			link.setCourse(savedCourse);
			link.setLevel(primaria);
			courseLevelRepository.saveAndFlush(link);

			Teacher teacher = new Teacher();
			teacher.setFirstName("María");
			teacher.setLastName("García");
			teacher.setDocumentType(DocumentType.DNI);
			teacher.setDocumentNumber("12345678" + tenant.getSlug().substring(0, 1));
			teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);
			Teacher savedTeacher = teacherRepository.saveAndFlush(teacher);

			TeacherAssignment assignment = new TeacherAssignment();
			assignment.setTeacher(savedTeacher);
			assignment.setSection(savedSection);
			assignment.setCourse(savedCourse);
			assignment.setAcademicPeriod(savedPeriod);
			assignment.setAssignedAt(Instant.now());
			TeacherAssignment savedAssignment = assignmentRepository
					.saveAndFlush(assignment);

			TimeSlot slot = new TimeSlot();
			slot.setTeacherAssignment(savedAssignment);
			slot.setDayOfWeek((short) 1);
			slot.setStartTime(LocalTime.of(8, 0));
			slot.setEndTime(LocalTime.of(9, 0));
			slot.setClassroom("Aula 12");
			timeSlotRepository.saveAndFlush(slot);

			return new AssignmentBundle(savedTeacher, savedSection, savedCourse,
					savedPeriod, savedAssignment);
		}));
	}
}
