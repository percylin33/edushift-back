package com.edushift.modules.evaluations.graderecord;

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
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
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
import java.math.BigDecimal;
import java.time.Instant;
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
 * Cross-tenant isolation IT for the {@code evaluations.graderecord}
 * sub-module (Sprint 5B / BE-5B.3).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A's listing under their
 *       evaluation is bounded; B's grades never leak.</li>
 *   <li><strong>Cross-tenant access</strong> — GET / PUT / DELETE on
 *       B's grade UUID from A → 404 (anti-enumeration).</li>
 *   <li><strong>Cross-tenant evaluation reference</strong> — POST under
 *       B's evaluation UUID from A → 404.</li>
 * </ul>
 *
 * <p>Like its siblings ({@code EvaluationTenantIsolationIT},
 * {@code RubricTenantIsolationIT}), this IT requires Docker because
 * {@link IntegrationTest} bootstraps a real Postgres container.
 * Compiles offline; running needs Docker Desktop up.
 */
@DisplayName("GradeRecord multi-tenancy isolation")
class GradeRecordTenantIsolationIT extends IntegrationTest {

    private static final String EVAL_BASE = "/v1/academic/evaluations";
    private static final String GRADE_FLAT_BASE = "/v1/academic/grade-records";
    private static final String AUTH_BASE = "/v1/auth";
    private static final String SHARED_EMAIL = "shared-grade@isolation.test";
    private static final String PASSWORD_A = "PassGradeA-1!";
    private static final String PASSWORD_B = "PassGradeB-2!";

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
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentEnrollmentRepository enrollmentRepository;
    @Autowired private GradeRecordRepository gradeRecordRepository;
    @Autowired private AcademicSeedService seedService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx;

    private TransactionTemplate tx() {
        if (tx == null) tx = new TransactionTemplate(txManager);
        return tx;
    }

    // -------------------------------------------------------------------
    // Read isolation
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("Read isolation")
    class Read {

        @Test
        @DisplayName("Admin A listing grades of A's evaluation does not leak B's rows")
        void listIsScoped() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doGet(
                    EVAL_BASE + "/" + fx.evaluationA().getPublicUuid()
                            + "/grade-records",
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());

            List<UUID> bGradeIds = TenantContext.runAs(fx.tenantB().getId(),
                    () -> tx().execute(s -> gradeRecordRepository.findAll().stream()
                            .map(GradeRecord::getPublicUuid).toList()));

            assertThat(array).isNotNull();
            for (JsonNode item : array) {
                UUID id = UUID.fromString(item.get("publicUuid").asText());
                assertThat(bGradeIds)
                        .as("tenant B grade publicUuid leaked into A's listing")
                        .doesNotContain(id);
            }
        }

        @Test
        @DisplayName("Admin A reading B's grade by publicUuid → 404")
        void crossTenantGetIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doGet(
                    GRADE_FLAT_BASE + "/" + fx.gradeB().getPublicUuid(),
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Admin A listing grades of B's evaluation → 404")
        void crossTenantEvaluationIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doGet(
                    EVAL_BASE + "/" + fx.evaluationB().getPublicUuid()
                            + "/grade-records",
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // -------------------------------------------------------------------
    // Write isolation
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("Write isolation")
    class Write {

        @Test
        @DisplayName("Cross-tenant POST under B's evaluation → 404")
        void crossTenantPostEvaluationIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            String body = "{"
                    + "\"studentPublicUuid\":\"" + fx.studentA().getPublicUuid() + "\","
                    + "\"score\":17.50"
                    + "}";

            ResponseEntity<String> response = doPost(
                    EVAL_BASE + "/" + fx.evaluationB().getPublicUuid()
                            + "/grade-records",
                    loginA.accessToken(), body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Cross-tenant DELETE → 404")
        void crossTenantDeleteIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doDelete(
                    GRADE_FLAT_BASE + "/" + fx.gradeB().getPublicUuid(),
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Cross-tenant PUT → 404")
        void crossTenantPutIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            String body = "{\"comments\":\"hijack\"}";
            ResponseEntity<String> response = rest.exchange(
                    GRADE_FLAT_BASE + "/" + fx.gradeB().getPublicUuid(),
                    HttpMethod.PUT,
                    new HttpEntity<>(body, jsonHeadersWithAuth(loginA.accessToken())),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // -------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------

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
        return rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
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

    // -------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------

    record Fixture(
            Tenant tenantA, Tenant tenantB,
            Evaluation evaluationA, Evaluation evaluationB,
            Student studentA, Student studentB,
            GradeRecord gradeA, GradeRecord gradeB
    ) {
    }

    private Fixture setupTenants() {
        Tenant tenantA = createTenant("it-grade-a-");
        Tenant tenantB = createTenant("it-grade-b-");
        User adminA = createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
        User adminB = createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
        seedAcademicCatalog(tenantA);
        seedAcademicCatalog(tenantB);

        Bundle bundleA = seedGrade(tenantA, adminA);
        Bundle bundleB = seedGrade(tenantB, adminB);

        return new Fixture(tenantA, tenantB,
                bundleA.evaluation(), bundleB.evaluation(),
                bundleA.student(), bundleB.student(),
                bundleA.grade(), bundleB.grade());
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

    record Bundle(Evaluation evaluation, Student student, GradeRecord grade) {
    }

    private Bundle seedGrade(Tenant tenant, User admin) {
        return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
            AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
                    .orElseThrow();
            Grade grade = gradeRepository
                    .findAllByLevelOrderByOrdinalAsc(primaria).get(0);

            AcademicYear year = new AcademicYear();
            year.setName("2026-IT-GRADE");
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
            course.setCode("MAT-IT-G");
            course.setName("Matemática IT GradeRec");
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
            teacher.setDocumentNumber("87654321"
                    + tenant.getSlug().substring(0, 1).toUpperCase());
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

            // Evaluation in PUBLISHED status so the grade write can land.
            LocalDate scheduledDate = LocalDate.of(2026, 4, 10);
            Evaluation evaluation = new Evaluation();
            evaluation.setTeacherAssignment(savedAssignment);
            evaluation.setKind(EvaluationKind.TASK);
            evaluation.setName("Tarea 1");
            evaluation.setWeight(BigDecimal.valueOf(1.00));
            evaluation.setScheduledDate(scheduledDate);
            evaluation.setScale(EvaluationScale.SCORE_0_20);
            evaluation.setStatus(EvaluationStatus.PUBLISHED);
            evaluation.setPublishedAt(Instant.now());
            evaluation.setIsActive(Boolean.TRUE);
            Evaluation savedEvaluation = evaluationRepository
                    .saveAndFlush(evaluation);

            // A student matriculated in the same section from the year start.
            Student student = new Student();
            student.setDocumentType(DocumentType.DNI);
            student.setDocumentNumber("12345678"
                    + tenant.getSlug().substring(0, 1).toUpperCase());
            student.setFirstName("Ana");
            student.setLastName("Pérez");
            Student savedStudent = studentRepository.saveAndFlush(student);

            StudentEnrollment enrollment = new StudentEnrollment();
            enrollment.setStudent(savedStudent);
            enrollment.setSection(savedSection);
            enrollment.setAcademicYear(savedYear);
            enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
            enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
            enrollmentRepository.saveAndFlush(enrollment);

            GradeRecord gradeRecord = new GradeRecord();
            gradeRecord.setEvaluation(savedEvaluation);
            gradeRecord.setStudent(savedStudent);
            gradeRecord.setScore(new BigDecimal("17.50"));
            gradeRecord.setRecordedAt(Instant.now());
            gradeRecord.setRecordedByUserId(admin.getId());
            gradeRecord.setIsActive(Boolean.TRUE);
            GradeRecord savedGrade = gradeRecordRepository.saveAndFlush(gradeRecord);

            return new Bundle(savedEvaluation, savedStudent, savedGrade);
        }));
    }
}
