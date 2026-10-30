package com.edushift.modules.lms;

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
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.entity.TaskStatus;
import com.edushift.modules.tasks.repository.TaskRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
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
 * V105 course-scoped LMS assignment isolation IT.
 *
 * <p>Validates that tasks created under a {@code TeacherAssignment} are
 * filtered by {@code assignmentPublicUuid} on admin list endpoints, that
 * the binder rejects section mismatches with
 * {@code ASSIGNMENT_SECTION_MISMATCH}, and that STUDENT {@code /me/sections}
 * cards / detail are assignment-scoped (PUBLISHED only).
 */
@DisplayName("LMS assignment scope (V105)")
class LmsAssignmentScopeIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";
	private static final String SECTIONS_TASKS = "/v1/sections/{section}/tasks";
	private static final String TASKS_BASE = "/v1/tasks";
	private static final String ME_SECTIONS = "/v1/me/sections";

	private static final String ADMIN_EMAIL = "admin@lms-scope.test";
	private static final String ADMIN_PASSWORD = "PassLmsAdmin-1!";
	private static final String TEACHER_EMAIL = "teacher@lms-scope.test";
	private static final String TEACHER_PASSWORD = "PassLmsTeacher-1!";
	private static final String STUDENT_EMAIL = "student@lms-scope.test";
	private static final String STUDENT_PASSWORD = "PassLmsStudent-1!";

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
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private PlatformPlanRepository platformPlanRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Admin create / list scope
	// =========================================================================

	@Nested
	@DisplayName("Admin create + list scoped by assignment")
	class AdminScope {

		@Test
		@DisplayName("create on assignment1 → list(assignment1) includes it; list(assignment2) does not")
		void createAndListAreAssignmentScoped() throws Exception {
			Fixture fx = setupFixture();
			AuthResponse admin = login(fx.tenant().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);

			String body = createTaskJson("Tarea Algebra", fx.assignment1().getPublicUuid());
			ResponseEntity<String> created = doPost(
					SECTIONS_TASKS.replace("{section}", fx.section().getPublicUuid().toString()),
					admin.accessToken(), body);

			assertThat(created.getStatusCode().is2xxSuccessful())
					.as("create body=%s", created.getBody())
					.isTrue();
			JsonNode createdData = objectMapper.readTree(created.getBody()).get("data");
			UUID taskUuid = UUID.fromString(createdData.get("publicUuid").asText());

			ResponseEntity<String> list1 = doGet(
					SECTIONS_TASKS.replace("{section}", fx.section().getPublicUuid().toString())
							+ "?assignmentPublicUuid=" + fx.assignment1().getPublicUuid(),
					admin.accessToken());
			assertThat(list1.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode content1 = objectMapper.readTree(list1.getBody()).get("content");
			assertThat(content1.isArray()).isTrue();
			assertThat(uuidsIn(content1)).contains(taskUuid);

			ResponseEntity<String> list2 = doGet(
					SECTIONS_TASKS.replace("{section}", fx.section().getPublicUuid().toString())
							+ "?assignmentPublicUuid=" + fx.assignment2().getPublicUuid(),
					admin.accessToken());
			assertThat(list2.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode content2 = objectMapper.readTree(list2.getBody()).get("content");
			assertThat(content2.isArray()).isTrue();
			assertThat(uuidsIn(content2)).doesNotContain(taskUuid);
		}

		@Test
		@DisplayName("create with assignment of another section → 400 ASSIGNMENT_SECTION_MISMATCH")
		void createWithWrongSectionAssignmentIs400() throws Exception {
			Fixture fx = setupFixture();
			AuthResponse admin = login(fx.tenant().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);

			String body = createTaskJson("Tarea Hacker", fx.assignmentOtherSection().getPublicUuid());
			ResponseEntity<String> response = doPost(
					SECTIONS_TASKS.replace("{section}", fx.section().getPublicUuid().toString()),
					admin.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			JsonNode errors = objectMapper.readTree(response.getBody()).get("errors");
			assertThat(errors.isArray()).isTrue();
			assertThat(errors.get(0).get("code").asText())
					.isEqualTo("ASSIGNMENT_SECTION_MISMATCH");
		}
	}

	// =========================================================================
	// Student /me surfaces
	// =========================================================================

	@Nested
	@DisplayName("Student /me/sections assignment cards")
	class MeScope {

		@Test
		@DisplayName("GET /me/sections → 2 cards with distinct assignment uuids; taskCount only on assignment1")
		void listMySectionsHasTwoAssignmentCards() throws Exception {
			Fixture fx = setupFixture();
			AuthResponse admin = login(fx.tenant().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);

			String createBody = createTaskJson("Tarea Visible", fx.assignment1().getPublicUuid());
			ResponseEntity<String> created = doPost(
					SECTIONS_TASKS.replace("{section}", fx.section().getPublicUuid().toString()),
					admin.accessToken(), createBody);
			assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
			UUID taskUuid = UUID.fromString(objectMapper.readTree(created.getBody())
					.get("data").get("publicUuid").asText());

			ResponseEntity<String> published = doPost(
					TASKS_BASE + "/" + taskUuid + "/publish",
					admin.accessToken(), "{}");
			assertThat(published.getStatusCode().is2xxSuccessful())
					.as("publish body=%s", published.getBody())
					.isTrue();

			AuthResponse student = login(fx.tenant().getSlug(), STUDENT_EMAIL, STUDENT_PASSWORD);
			ResponseEntity<String> response = doGet(ME_SECTIONS, student.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode cards = objectMapper.readTree(response.getBody()).get("data");
			assertThat(cards.isArray()).isTrue();
			assertThat(cards.size()).isEqualTo(2);

			Set<UUID> assignmentIds = new HashSet<>();
			JsonNode card1 = null;
			JsonNode card2 = null;
			for (JsonNode card : cards) {
				UUID aUuid = UUID.fromString(card.get("teacherAssignmentPublicUuid").asText());
				assignmentIds.add(aUuid);
				if (aUuid.equals(fx.assignment1().getPublicUuid())) {
					card1 = card;
				}
				if (aUuid.equals(fx.assignment2().getPublicUuid())) {
					card2 = card;
				}
			}
			assertThat(assignmentIds)
					.containsExactlyInAnyOrder(
							fx.assignment1().getPublicUuid(),
							fx.assignment2().getPublicUuid());
			assertThat(card1).isNotNull();
			assertThat(card2).isNotNull();
			assertThat(card1.get("taskCount").asLong()).isEqualTo(1L);
			assertThat(card2.get("taskCount").asLong()).isEqualTo(0L);
		}

		@Test
		@DisplayName("GET /me/sections/{section}?assignment=a1 → only published task; DRAFT hidden")
		void detailFiltersByAssignmentAndHidesDraft() throws Exception {
			Fixture fx = setupFixture();
			AuthResponse admin = login(fx.tenant().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD);

			String createBody = createTaskJson("Tarea Publicado", fx.assignment1().getPublicUuid());
			ResponseEntity<String> created = doPost(
					SECTIONS_TASKS.replace("{section}", fx.section().getPublicUuid().toString()),
					admin.accessToken(), createBody);
			assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
			UUID publishedUuid = UUID.fromString(objectMapper.readTree(created.getBody())
					.get("data").get("publicUuid").asText());

			ResponseEntity<String> published = doPost(
					TASKS_BASE + "/" + publishedUuid + "/publish",
					admin.accessToken(), "{}");
			assertThat(published.getStatusCode().is2xxSuccessful()).isTrue();

			UUID draftUuid = seedDraftTask(fx, "Tarea Borrador");

			AuthResponse student = login(fx.tenant().getSlug(), STUDENT_EMAIL, STUDENT_PASSWORD);
			ResponseEntity<String> response = doGet(
					ME_SECTIONS + "/" + fx.section().getPublicUuid()
							+ "?assignment=" + fx.assignment1().getPublicUuid(),
					student.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(UUID.fromString(data.get("teacherAssignmentPublicUuid").asText()))
					.isEqualTo(fx.assignment1().getPublicUuid());

			JsonNode tasks = data.get("tasks");
			assertThat(tasks.isArray()).isTrue();
			Set<UUID> taskIds = uuidsIn(tasks);
			assertThat(taskIds).contains(publishedUuid);
			assertThat(taskIds).doesNotContain(draftUuid);
			assertThat(taskIds).hasSize(1);
		}
	}

	// =========================================================================
	// HTTP helpers
	// =========================================================================

	private String createTaskJson(String title, UUID assignmentPublicUuid) {
		return String.format(
				"{\"title\":\"%s\",\"description\":\"desc-%s\","
						+ "\"dueAt\":\"%s\",\"maxScore\":100,"
						+ "\"teacherAssignmentPublicUuid\":\"%s\"}",
				title, title,
				Instant.now().plus(7, ChronoUnit.DAYS),
				assignmentPublicUuid);
	}

	private Set<UUID> uuidsIn(JsonNode array) {
		Set<UUID> out = new HashSet<>();
		for (JsonNode item : array) {
			out.add(UUID.fromString(item.get("publicUuid").asText()));
		}
		return out;
	}

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
		return readSessionResponse(objectMapper, response);
	}

	private ResponseEntity<String> doGet(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> doPost(String path, String bearer, String body) {
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, jsonHeadersWithAuth(bearer)), String.class);
	}

	// =========================================================================
	// Fixture
	// =========================================================================

	record Fixture(
			Tenant tenant,
			User admin,
			User teacherUser,
			Teacher teacher,
			Section section,
			Section otherSection,
			Course course1,
			Course course2,
			TeacherAssignment assignment1,
			TeacherAssignment assignment2,
			TeacherAssignment assignmentOtherSection,
			Student student
	) {}

	private Fixture setupFixture() {
		Tenant tenant = createTenant("it-lms-scope-");
		User admin = createUser(tenant, ADMIN_EMAIL, ADMIN_PASSWORD, UserRole.TENANT_ADMIN);
		User teacherUser = createUser(tenant, TEACHER_EMAIL, TEACHER_PASSWORD, UserRole.TEACHER);
		User studentUser = createUser(tenant, STUDENT_EMAIL, STUDENT_PASSWORD, UserRole.STUDENT);
		seedAcademicCatalog(tenant);
		return seedGraph(tenant, admin, teacherUser, studentUser);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = newTestTenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		t.setPlanId(platformPlanRepository.findByCode("BASIC")
				.orElseThrow(() -> new IllegalStateException(
						"platform_plans.BASIC not seeded — Flyway V54 did not run"))
				.getId());
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createUser(Tenant tenant, String email, String rawPassword, UserRole role) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash(passwordEncoder.encode(rawPassword));
					user.setFirstName("It");
					user.setLastName(role.name());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(role);
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

	private Fixture seedGraph(Tenant tenant, User admin, User teacherUser, User studentUser) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			Grade grade = gradeRepository
					.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

			AcademicYear year = new AcademicYear();
			year.setName("2026-IT-LMS-SCOPE");
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
			section.setDisplayOrder(1);
			Section savedSection = sectionRepository.saveAndFlush(section);

			Section otherSection = new Section();
			otherSection.setAcademicYear(savedYear);
			otherSection.setGrade(grade);
			otherSection.setName("1ro B");
			otherSection.setDisplayOrder(2);
			Section savedOtherSection = sectionRepository.saveAndFlush(otherSection);

			Course course1 = new Course();
			course1.setCode("MAT_LMS_1");
			course1.setName("Matemática Scope");
			course1.setIsActive(true);
			Course savedCourse1 = courseRepository.saveAndFlush(course1);

			Course course2 = new Course();
			course2.setCode("COM_LMS_2");
			course2.setName("Comunicación Scope");
			course2.setIsActive(true);
			Course savedCourse2 = courseRepository.saveAndFlush(course2);

			Course courseOther = new Course();
			courseOther.setCode("CIE_LMS_O");
			courseOther.setName("Ciencia Other Section");
			courseOther.setIsActive(true);
			Course savedCourseOther = courseRepository.saveAndFlush(courseOther);

			linkCourseLevel(savedCourse1, primaria);
			linkCourseLevel(savedCourse2, primaria);
			linkCourseLevel(savedCourseOther, primaria);

			Teacher teacher = new Teacher();
			teacher.setFirstName("María");
			teacher.setLastName("García");
			teacher.setDocumentType(DocumentType.DNI);
			teacher.setDocumentNumber("8765" + UUID.randomUUID().toString().substring(0, 4));
			teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);
			teacher.setUserId(teacherUser.getPublicUuid());
			Teacher savedTeacher = teacherRepository.saveAndFlush(teacher);

			TeacherAssignment assignment1 = new TeacherAssignment();
			assignment1.setTeacher(savedTeacher);
			assignment1.setSection(savedSection);
			assignment1.setCourse(savedCourse1);
			assignment1.setAcademicPeriod(savedPeriod);
			assignment1.setAssignedAt(Instant.now());
			TeacherAssignment savedA1 = assignmentRepository.saveAndFlush(assignment1);

			TeacherAssignment assignment2 = new TeacherAssignment();
			assignment2.setTeacher(savedTeacher);
			assignment2.setSection(savedSection);
			assignment2.setCourse(savedCourse2);
			assignment2.setAcademicPeriod(savedPeriod);
			assignment2.setAssignedAt(Instant.now());
			TeacherAssignment savedA2 = assignmentRepository.saveAndFlush(assignment2);

			TeacherAssignment assignmentOther = new TeacherAssignment();
			assignmentOther.setTeacher(savedTeacher);
			assignmentOther.setSection(savedOtherSection);
			assignmentOther.setCourse(savedCourseOther);
			assignmentOther.setAcademicPeriod(savedPeriod);
			assignmentOther.setAssignedAt(Instant.now());
			TeacherAssignment savedOther = assignmentRepository.saveAndFlush(assignmentOther);

			Student student = new Student();
			student.setDocumentType(DocumentType.DNI);
			student.setDocumentNumber("9000" + Math.abs(
					tenant.getId().getMostSignificantBits() % 10000));
			student.setFirstName("Ana");
			student.setLastName("Estudiante");
			student.setEmail(STUDENT_EMAIL);
			student.setUserId(studentUser.getPublicUuid());
			student.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
			Student savedStudent = studentRepository.saveAndFlush(student);

			StudentEnrollment enrollment = new StudentEnrollment();
			enrollment.setStudent(savedStudent);
			enrollment.setSection(savedSection);
			enrollment.setAcademicYear(savedYear);
			enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
			enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
			enrollmentRepository.saveAndFlush(enrollment);

			return new Fixture(
					tenant, admin, teacherUser, savedTeacher,
					savedSection, savedOtherSection,
					savedCourse1, savedCourse2,
					savedA1, savedA2, savedOther,
					savedStudent);
		}));
	}

	private void linkCourseLevel(Course course, AcademicLevel level) {
		CourseLevel link = new CourseLevel();
		link.setCourse(course);
		link.setLevel(level);
		courseLevelRepository.saveAndFlush(link);
	}

	/**
	 * Seeds a DRAFT task on assignment1 via the repository so the me-detail
	 * endpoint can prove drafts stay invisible to students.
	 */
	private UUID seedDraftTask(Fixture fx, String title) {
		return TenantContext.runAs(fx.tenant().getId(), () -> tx().execute(s -> {
			Task draft = new Task();
			draft.setSection(fx.section());
			draft.setTeacherAssignment(fx.assignment1());
			draft.setTitle(title);
			draft.setDescription("draft-desc");
			draft.setDueAt(Instant.now().plus(7, ChronoUnit.DAYS));
			draft.setOwnerUserId(fx.admin().getPublicUuid());
			draft.setAllowResubmission(true);
			draft.setStatus(TaskStatus.DRAFT);
			return taskRepository.saveAndFlush(draft).getPublicUuid();
		}));
	}
}
