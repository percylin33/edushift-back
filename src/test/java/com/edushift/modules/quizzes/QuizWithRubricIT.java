package com.edushift.modules.quizzes;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
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
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IT for the Quiz ↔ Rubric integration (Sprint 7b / BE-7b.3).
 *
 * <h3>Scenarios</h3>
 * <ol>
 *   <li><strong>RUBRIC-1</strong> Quiz without rubric
 *       → gradeWithRubric → 400 QUIZ_HAS_NO_RUBRIC; no
 *       {@code GradeRecord} written.</li>
 *   <li><strong>RUBRIC-2</strong> TEACHER attaches rubric
 *       → derived {@code Evaluation} row created with
 *       {@code [QuizRubric]} prefix; quiz response carries both
 *       {@code rubricPublicUuid} and
 *       {@code rubricEvaluationPublicUuid}.</li>
 *   <li><strong>RUBRIC-3</strong> After attaching, teacher grades
 *       a student attempt with {@code gradeWithRubric} → 200; a
 *       new {@code GradeRecord} appears anchored to the derived
 *       evaluation, with literal = the worst MINEDU level chosen.</li>
 *   <li><strong>RUBRIC-4</strong> Detach rubric (no grades yet)
 *       → quiz response carries nulls for both rubric fields;
 *       derived evaluation is soft-deleted.</li>
 *   <li><strong>RUBRIC-5</strong> Cross-tenant: teacher B attaches
 *       rubric to tenant A's quiz → 404 (anti-enumeration).</li>
 *   <li><strong>RUBRIC-6</strong> Unknown rubric publicUuid →
 *       404 RUBRIC_NOT_FOUND.</li>
 * </ol>
 */
@DisplayName("Quiz with rubric (BE-7b.3)")
class QuizWithRubricIT extends IntegrationTest {

	private static final String QUIZZES_BASE = "/v1/quizzes";
	private static final String ATTEMPTS_BASE = "/v1/attempts";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicPeriodRepository periodRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private RubricRepository rubricRepository;
	@Autowired private EvaluationRepository evaluationRepository;
	@Autowired private GradeRecordRepository gradeRecordRepository;
	@Autowired private QuizRepository quizRepository;
	@Autowired private QuizQuestionRepository questionRepository;
	@Autowired private QuizOptionRepository optionRepository;
	@Autowired private QuizAttemptRepository attemptRepository;
	@Autowired private JwtService jwtService;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Scenarios
	// =========================================================================

	@Nested
	@DisplayName("Quiz without rubric → numeric-only path is untouched")
	class NoRubricPath {

		@Test
		@DisplayName("RUBRIC-1: gradeWithRubric on rubric-less quiz → 400 + no GradeRecord")
		void gradeWithRubricRejectedWhenNoRubric() throws Exception {
			Fixture fixture = setupWithQuizNoRubric();
			// Create the in-progress attempt directly via the
			// repository — sidesteps the requireEnrolledStudent
			// gate, which is a separate concern covered by the
			// QuizPlayerAndGradingIT. This IT focuses on the
			// rubric integration.
			java.util.UUID attemptPublicUuid = createInProgressAttemptDirectly(fixture);

			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");
			String body = "{"
					+ "\"picks\":[{\"criterionKey\":\"redaccion\","
					+ "\"levelCode\":\"ESPERADO\"}],"
					+ "\"comments\":\"no rubric attached\"}";

			ResponseEntity<String> response = doPost(
					ATTEMPTS_BASE + "/" + attemptPublicUuid + "/grade-with-rubric",
					teacherBearer,
					body);

			assertThat(response.getStatusCode())
					.as("quiz has no rubric attached → 400 QUIZ_HAS_NO_RUBRIC")
					.isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody())
					.contains("QUIZ_HAS_NO_RUBRIC");

			// Defence-in-depth: no grade_records row was written.
			long gradeCount = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> gradeRecordRepository.count()));
			assertThat(gradeCount).isZero();
		}
	}

	@Nested
	@DisplayName("Quiz with rubric → derived evaluation + GradeRecord")
	class WithRubricPath {

		@Test
		@DisplayName("RUBRIC-2: attach rubric → derived Evaluation created with [QuizRubric] prefix")
		void attachRubricCreatesDerivedEvaluation() throws Exception {
			Fixture fixture = setupWithQuizNoRubric();
			Rubric rubric = createRubric(fixture.tenantA(), "r1");

			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");
			String body = String.format(
					"{\"rubricPublicUuid\":\"%s\"}", rubric.getPublicUuid());
			ResponseEntity<String> response = doPatch(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/rubric",
					teacherBearer,
					body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.get("rubricPublicUuid").asText())
					.isEqualTo(rubric.getPublicUuid().toString());
			assertThat(data.get("rubricEvaluationPublicUuid").asText())
					.isNotEmpty();

			// DB-level: exactly one non-deleted derived evaluation
			// for this quiz, with the [QuizRubric] prefix in name.
			UUID derivedUuid = UUID.fromString(
					data.get("rubricEvaluationPublicUuid").asText());
			com.edushift.modules.evaluations.entity.Evaluation derived = TenantContext
					.runAs(fixture.tenantA().getId(),
							() -> tx().execute(s -> evaluationRepository
									.findByPublicUuid(derivedUuid).orElseThrow()));
			assertThat(derived.getName())
					.startsWith("[QuizRubric] ");
			assertThat(derived.getKind().name())
					.isEqualTo("QUIZ");
			assertThat(derived.getScale().name())
					.isEqualTo("LITERAL_A_B_C_D");
			assertThat(derived.getStatus().name())
					.isEqualTo("PUBLISHED");
		}

		@Test
		@DisplayName("RUBRIC-3: gradeWithRubric → GradeRecord on derived evaluation, "
				+ "literal = worst chosen level (MINEDU conservative)")
		void gradeWithRubricWritesGradeRecord() throws Exception {
			Fixture fixture = setupWithQuizNoRubric();
			Rubric rubric = createRubric(fixture.tenantA(), "r1");

			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");
			// Attach the rubric.
			doPatch(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/rubric",
					teacherBearer,
					String.format("{\"rubricPublicUuid\":\"%s\"}",
							rubric.getPublicUuid()));

			// Create the in-progress attempt directly via the
			// repository — sidesteps the requireEnrolledStudent
			// gate, which is a separate concern covered by the
			// QuizPlayerAndGradingIT.
			java.util.UUID attemptPublicUuid = createInProgressAttemptDirectly(fixture);

			// TEACHER grades: picks SOBRESALIENTE on redaction (best)
			// and EN_INICIO on argumentacion (worst). The conservative
			// MINEDU rule picks the worst → literal = EN_INICIO.
			String gradeBody = "{"
					+ "\"picks\":["
					+ "  {\"criterionKey\":\"redaccion\",\"levelCode\":\"SOBRESALIENTE\"},"
					+ "  {\"criterionKey\":\"argumentacion\",\"levelCode\":\"EN_INICIO\"}"
					+ "],"
					+ "\"comments\":\"Faltó conectar ideas\""
					+ "}";
			ResponseEntity<String> response = doPost(
					ATTEMPTS_BASE + "/" + attemptPublicUuid + "/grade-with-rubric",
					teacherBearer,
					gradeBody);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

			// DB-level: exactly one GradeRecord on the derived eval.
			long gradeCount = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> gradeRecordRepository.count()));
			assertThat(gradeCount).isEqualTo(1L);

			// And the literal is C — MINEDU's conservative
			// "criterio mínimo" rule maps the worst chosen level
			// (EN_INICIO) to GradeRecord.literal = C (the most
			// restrictive literal allowed by the V27 CHECK
			// constraint).
			com.edushift.modules.evaluations.graderecord.entity.GradeRecord record =
					TenantContext.runAs(fixture.tenantA().getId(),
							() -> tx().execute(s -> gradeRecordRepository
									.findAll().get(0)));
			assertThat(record.getLiteral()).isEqualTo("C");
			assertThat(record.getComments()).isEqualTo("Faltó conectar ideas");
			// graderUserId is the JWT public UUID (V29 contract).
			assertThat(record.getRecordedByUserId())
					.isEqualTo(fixture.teacherA().getPublicUuid());
		}

		@Test
		@DisplayName("RUBRIC-4: detach rubric (no grades yet) → derived evaluation soft-deleted")
		void detachRubricSoftDeletesEmptyEvaluation() throws Exception {
			Fixture fixture = setupWithQuizNoRubric();
			Rubric rubric = createRubric(fixture.tenantA(), "r1");
			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");

			// Attach.
			ResponseEntity<String> attachResp = doPatch(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/rubric",
					teacherBearer,
					String.format("{\"rubricPublicUuid\":\"%s\"}",
							rubric.getPublicUuid()));
			UUID derivedUuid = UUID.fromString(objectMapper.readTree(attachResp.getBody())
					.get("data").get("rubricEvaluationPublicUuid").asText());

			// Detach.
			ResponseEntity<String> detachResp = doDelete(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/rubric",
					teacherBearer);

			assertThat(detachResp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(detachResp.getBody()).get("data");
			// @JsonInclude(NON_NULL) hides null fields, so after
			// detach the fields are absent (not null).
			assertThat(data.has("rubricPublicUuid"))
					.as("rubricPublicUuid is absent (or null) after detach")
					.isFalse();
			assertThat(data.has("rubricEvaluationPublicUuid"))
					.as("rubricEvaluationPublicUuid is absent (or null) after detach")
					.isFalse();

			// The derived evaluation is soft-deleted (deleted=true).
			// We need findByPublicUuidAcrossTenants because
			// @SQLRestriction hides the row from findByPublicUuid once
			// it's marked deleted.
			com.edushift.modules.evaluations.entity.Evaluation derived = TenantContext
					.runAs(fixture.tenantA().getId(),
							() -> tx().execute(s -> evaluationRepository
									.findByPublicUuidAcrossTenants(derivedUuid)
									.orElseThrow()));
			assertThat(derived.isDeleted()).isTrue();
		}

		@Test
		@DisplayName("RUBRIC-5: cross-tenant attach (B attaches rubric to A's quiz) → 404")
		void crossTenantAttachReturns404() throws Exception {
			Fixture fixture = setupWithQuizNoRubric();
			Rubric rubricB = createRubric(fixture.tenantB(), "rB");

			String teacherB = bearerFor(fixture.teacherB(), fixture.tenantB(), "TEACHER");
			ResponseEntity<String> response = doPatch(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/rubric",
					teacherB,
					String.format("{\"rubricPublicUuid\":\"%s\"}",
							rubricB.getPublicUuid()));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("RUBRIC-6: unknown rubric publicUuid → 404 RUBRIC_NOT_FOUND")
		void unknownRubricReturns404() throws Exception {
			Fixture fixture = setupWithQuizNoRubric();
			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");

			ResponseEntity<String> response = doPatch(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/rubric",
					teacherBearer,
					String.format("{\"rubricPublicUuid\":\"%s\"}",
							UUID.randomUUID()));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(response.getBody()).contains("RUBRIC_NOT_FOUND");
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

	private String bearerFor(User user, Tenant tenant, String roleName) {
		return jwtService.issueAccessToken(user, tenant, Set.of(roleName));
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

	/**
	 * Builds an in-progress {@link com.edushift.modules.quizzes.entity.QuizAttempt}
	 * directly via the repository, sidestepping the
	 * {@code requireEnrolledStudent} gate. This keeps the IT focused
	 * on the rubric integration (BE-7b.3) — the player
	 * authorization gate is exercised by {@code QuizPlayerAndGradingIT}.
	 */
	private java.util.UUID createInProgressAttemptDirectly(Fixture fixture) {
		return TenantContext.runAs(fixture.tenantA().getId(),
				() -> tx().execute(s -> {
					com.edushift.modules.quizzes.entity.QuizAttempt attempt =
							new com.edushift.modules.quizzes.entity.QuizAttempt();
					attempt.setQuiz(fixture.quizA());
					attempt.setStudentUserId(fixture.studentA().getPublicUuid());
					attempt.setSubmitterUserId(fixture.studentA().getPublicUuid());
					attempt.setAttemptNumber((short) 1);
					attempt.setStatus(
							com.edushift.modules.quizzes.entity.AttemptStatus.IN_PROGRESS);
					attempt.setStartedAt(Instant.now());
					attempt = attemptRepository.saveAndFlush(attempt);
					return attempt.getPublicUuid();
				}));
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			User teacherA, User teacherB,
			User studentA, User studentB,
			Quiz quizA
	) {}

	private Fixture setupWithQuizNoRubric() {
		Tenant tenantA = createTenant("it-rub-a-");
		Tenant tenantB = createTenant("it-rub-b-");

		User teacherA = createUser(tenantA, "teacher-a@rubric.test", "PassTA-1!", "TEACHER");
		User teacherB = createUser(tenantB, "teacher-b@rubric.test", "PassTB-2!", "TEACHER");
		User studentA = createUser(tenantA, "student-a@rubric.test", "PassSA-1!", "STUDENT");
		User studentB = createUser(tenantB, "student-b@rubric.test", "PassSB-2!", "STUDENT");

		// Tie tenant A's users to Teacher / Student records (so the
		// QuizRubricServiceImpl can resolve the owner's Teacher +
		// active assignment in the section).
		Teacher teacherEntityA = linkTeacher(tenantA, teacherA.getId(), "T-A");
		Teacher teacherEntityB = linkTeacher(tenantB, teacherB.getId(), "T-B");
		Student studentEntityA = linkStudent(tenantA, studentA.getId(), "S-A");
		Student studentEntityB = linkStudent(tenantB, studentB.getId(), "S-B");

		// Academic scaffold for tenant A.
		seedAcademicCatalog(tenantA);
		AcademicYear yearA = activateNewYear(tenantA, "2026");
		AcademicPeriod periodA = activateBimester(tenantA, yearA, 1);
		Course courseA = createCourse(tenantA, "MAT", "Matemática");
		Grade gradeA = firstGradeOfPrimaria(tenantA);
		Section sectionA = createSection(tenantA, yearA, gradeA, "A");

		// Active assignment: teacher A teaches course MAT in section A
		// during bimester 1.
		assignTeacherToSection(teacherEntityA, sectionA, courseA, periodA);
		enrollStudent(studentEntityA, sectionA, yearA);

		// Mirror the academic scaffold for tenant B (so cross-tenant
		// probes can verify isolation).
		seedAcademicCatalog(tenantB);
		AcademicYear yearB = activateNewYear(tenantB, "2026");
		AcademicPeriod periodB = activateBimester(tenantB, yearB, 1);
		Course courseB = createCourse(tenantB, "MAT", "Matemática");
		Grade gradeB = firstGradeOfPrimaria(tenantB);
		Section sectionB = createSection(tenantB, yearB, gradeB, "A");
		assignTeacherToSection(teacherEntityB, sectionB, courseB, periodB);
		enrollStudent(studentEntityB, sectionB, yearB);

		// The quiz itself: DRAFT, owner = teacher A.
		Quiz quizA = createDraftQuiz(tenantA, sectionA, teacherA);

		return new Fixture(tenantA, tenantB,
				teacherA, teacherB, studentA, studentB, quizA);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createUser(Tenant tenant, String email, String rawPassword, String roleName) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash("$2a$10$dummy.hash.for.it.only.satisfies.not.null");
					user.setFirstName(roleName);
					user.setLastName(tenant.getSlug());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.valueOf(roleName));
					return userRepository.saveAndFlush(user);
				}));
	}

	private Teacher linkTeacher(Tenant tenant, UUID userInternalId, String firstName) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Teacher teacher = new Teacher();
			teacher.setDocumentType(DocumentType.DNI);
			teacher.setDocumentNumber("T-" + Math.abs(
					userInternalId.hashCode() % 1000000));
			teacher.setFirstName(firstName);
			teacher.setLastName(tenant.getSlug());
			teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);
			// The FK teachers.user_id → users.id (V18).
			teacher.setUserId(userInternalId);
			return teacherRepository.saveAndFlush(teacher);
		}));
	}

	private Student linkStudent(Tenant tenant, UUID userInternalId, String firstName) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Student student = new Student();
			student.setDocumentType(DocumentType.DNI);
			student.setDocumentNumber("S-" + Math.abs(
					userInternalId.hashCode() % 1000000));
			student.setFirstName(firstName);
			student.setLastName(tenant.getSlug());
			// The FK students.user_id → users.id (V10).
			student.setUserId(userInternalId);
			return studentRepository.saveAndFlush(student);
		}));
	}

	private void seedAcademicCatalog(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					AcademicLevel level = new AcademicLevel();
					level.setCode("PRIMARIA");
					level.setName("Primaria");
					level.setOrdinal(2);
					level = levelRepository.saveAndFlush(level);
					Grade grade = new Grade();
					grade.setName("2do Primaria");
					grade.setOrdinal(2);
					grade.setLevel(level);
					gradeRepository.saveAndFlush(grade);
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

	private AcademicPeriod activateBimester(Tenant tenant, AcademicYear year, int ordinal) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicPeriod p = new AcademicPeriod();
			p.setAcademicYear(year);
			p.setPeriodType(PeriodType.BIMESTRE);
			p.setOrdinal(ordinal);
			p.setName("B" + ordinal);
			p.setStartDate(LocalDate.of(2026, 3, 1));
			p.setEndDate(LocalDate.of(2026, 4, 30));
			return periodRepository.saveAndFlush(p);
		}));
	}

	private Course createCourse(Tenant tenant, String code, String name) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Course c = new Course();
			c.setCode(code);
			c.setName(name);
			return courseRepository.saveAndFlush(c);
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

	private void assignTeacherToSection(Teacher teacher, Section section, Course course,
			AcademicPeriod period) {
		TenantContext.runAs(teacher.getTenantId(), () -> tx().execute(s -> {
			TeacherAssignment a = new TeacherAssignment();
			a.setTeacher(teacher);
			a.setSection(section);
			a.setCourse(course);
			a.setAcademicPeriod(period);
			a.setAssignedAt(Instant.now());
			assignmentRepository.saveAndFlush(a);
			return null;
		}));
	}

	private void enrollStudent(Student student, Section section, AcademicYear year) {
		TenantContext.runAs(student.getTenantId(), () -> tx().execute(s -> {
			StudentEnrollment enrollment = new StudentEnrollment();
			enrollment.setStudent(student);
			enrollment.setSection(section);
			enrollment.setAcademicYear(year);
			enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
			enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
			enrollmentRepository.saveAndFlush(enrollment);
			return null;
		}));
	}

	/**
	 * Builds a minimal valid {@code Rubric} (1..10 criteria, 2..4
	 * levels, weights sum to 100.0) directly through the repository,
	 * bypassing the validation service. This keeps the IT focused on
	 * the integration glue and not on the rubric's own validation
	 * rules (which are already covered by RubricServiceImpl tests).
	 */
	private Rubric createRubric(Tenant tenant, String label) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Rubric rubric = new Rubric();
			rubric.setName("IT rubric " + label + " " + tenant.getSlug());
			rubric.setDescription("Created by QuizWithRubricIT");
			rubric.setIsSystem(false);
			rubric.setIsActive(true);
			// criteria + levels are JSONB arrays of maps. We shape
			// them the same way RubricSeedService materialises them.
			java.util.List<java.util.Map<String, Object>> criteria = new java.util.ArrayList<>();
			criteria.add(java.util.Map.of(
					"key", "redaccion",
					"name", "Redacción",
					"description", "Claridad y cohesión",
					"weight", 60.0));
			criteria.add(java.util.Map.of(
					"key", "argumentacion",
					"name", "Argumentación",
					"description", "Sostiene su postura con razones",
					"weight", 40.0));
			rubric.setCriteria(criteria);

			java.util.List<java.util.Map<String, Object>> levels = new java.util.ArrayList<>();
			levels.add(java.util.Map.of(
					"code", "EN_INICIO", "name", "En inicio", "order", 1));
			levels.add(java.util.Map.of(
					"code", "EN_PROCESO", "name", "En proceso", "order", 2));
			levels.add(java.util.Map.of(
					"code", "ESPERADO", "name", "Esperado", "order", 3));
			levels.add(java.util.Map.of(
					"code", "SOBRESALIENTE", "name", "Sobresaliente", "order", 4));
			rubric.setLevels(levels);
			return rubricRepository.saveAndFlush(rubric);
		}));
	}

	private Quiz createDraftQuiz(Tenant tenant, Section section, User owner) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Quiz quiz = new Quiz();
			quiz.setSection(section);
			quiz.setTitle("Rubric IT " + tenant.getSlug());
			quiz.setDescription("Quiz for the BE-7b.3 IT");
			quiz.setMaxScore((short) 20);
			quiz.setAttemptsAllowed((short) 1);
			quiz.setOwnerUserId(owner.getPublicUuid());
			// status defaults to DRAFT in @PrePersist.
			quiz = quizRepository.saveAndFlush(quiz);

			// Add one MC question so the quiz is well-formed.
			QuizQuestion q = new QuizQuestion();
			q.setQuiz(quiz);
			q.setPosition((short) 1);
			q.setQuestionType(com.edushift.modules.quizzes.entity.QuestionType.MC);
			q.setPrompt("2+2?");
			q.setPoints((short) 5);
			questionRepository.saveAndFlush(q);
			com.edushift.modules.quizzes.entity.QuizOption opt =
					new com.edushift.modules.quizzes.entity.QuizOption();
			opt.setQuestion(q);
			opt.setLabel("4");
			opt.setCorrect(true);
			opt.setPosition((short) 1);
			optionRepository.saveAndFlush(opt);

			// Promote to PUBLISHED so POST /attempts can run. We
			// bypass QuizService.publish and write the lifecycle
			// columns directly — keeps the IT focused on the rubric
			// integration and not on the publish validation rules.
			quiz.setStatus(QuizStatus.PUBLISHED);
			quiz.setPublishedAt(java.time.Instant.now());
			quiz = quizRepository.saveAndFlush(quiz);
			return quiz;
		}));
	}
}
