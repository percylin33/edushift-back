package com.edushift.modules.evaluations.evaluationrubric;

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
import com.edushift.modules.evaluations.evaluationrubric.entity.EvaluationRubric;
import com.edushift.modules.evaluations.evaluationrubric.repository.EvaluationRubricRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * Cross-tenant isolation IT for the {@code evaluations.evaluation_rubric}
 * sub-module (Sprint 5B / BE-5B.4).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Happy path</strong> — admin A attaches A's rubric to
 *       A's evaluation, GETs it back.</li>
 *   <li><strong>Cross-tenant rubric</strong> — admin A tries to attach
 *       B's rubric to A's evaluation → 404 (the rubric load itself
 *       fails under A's TenantContext).</li>
 *   <li><strong>Cross-tenant evaluation</strong> — admin A operates on
 *       B's evaluation publicUuid → 404 (GET, POST, DELETE).</li>
 *   <li><strong>EVAL_RUBRIC_NOT_SET</strong> — admin A GETs the
 *       attached rubric on an A's evaluation that has none → 404 with
 *       the dedicated code.</li>
 * </ul>
 *
 * <p>Like {@code EvaluationTenantIsolationIT} and
 * {@code GradeRecordTenantIsolationIT}, this IT requires Docker because
 * {@link IntegrationTest} bootstraps a real Postgres container.
 * Compiles offline; running needs Docker Desktop up.
 */
@DisplayName("EvaluationRubric multi-tenancy isolation")
class EvaluationRubricTenantIsolationIT extends IntegrationTest {

    private static final String EVAL_BASE = "/v1/academic/evaluations";
    private static final String AUTH_BASE = "/v1/auth";
    private static final String SHARED_EMAIL = "shared-rubric-link@isolation.test";
    private static final String PASSWORD_A = "PassRubricLinkA-1!";
    private static final String PASSWORD_B = "PassRubricLinkB-2!";

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
    @Autowired private RubricRepository rubricRepository;
    @Autowired private EvaluationRubricRepository linkRepository;
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
    // Happy path
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("Happy path")
    class Happy {

        @Test
        @DisplayName("Admin A attaches A's rubric to A's evaluation, then GETs it")
        void attachAndGet() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            String body = "{\"rubricPublicUuid\":\""
                    + fx.rubricA().getPublicUuid() + "\"}";
            ResponseEntity<String> attach = doPost(
                    EVAL_BASE + "/" + fx.evaluationA().getPublicUuid() + "/rubric",
                    loginA.accessToken(), body);
            assertThat(attach.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<String> get = doGet(
                    EVAL_BASE + "/" + fx.evaluationA().getPublicUuid() + "/rubric",
                    loginA.accessToken());
            assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get.getBody()).contains(fx.rubricA().getPublicUuid().toString());
        }

        @Test
        @DisplayName("GET on A's evaluation without an attached rubric → 404 EVAL_RUBRIC_NOT_SET")
        void notSet() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doGet(
                    EVAL_BASE + "/" + fx.evaluationA().getPublicUuid() + "/rubric",
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).contains("EVAL_RUBRIC_NOT_SET");
        }
    }

    // -------------------------------------------------------------------
    // Cross-tenant
    // -------------------------------------------------------------------

    @Nested
    @DisplayName("Cross-tenant")
    class CrossTenant {

        @Test
        @DisplayName("Admin A attaches B's rubric to A's evaluation → 404 RESOURCE_NOT_FOUND")
        void crossTenantRubricIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            String body = "{\"rubricPublicUuid\":\""
                    + fx.rubricB().getPublicUuid() + "\"}";
            ResponseEntity<String> response = doPost(
                    EVAL_BASE + "/" + fx.evaluationA().getPublicUuid() + "/rubric",
                    loginA.accessToken(), body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Admin A reads rubric attached to B's evaluation → 404")
        void crossTenantGetEvaluationIs404() throws Exception {
            Fixture fx = setupTenantsWithLink();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doGet(
                    EVAL_BASE + "/" + fx.evaluationB().getPublicUuid() + "/rubric",
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Admin A detaches the rubric of B's evaluation → 404")
        void crossTenantDetachIs404() throws Exception {
            Fixture fx = setupTenantsWithLink();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            ResponseEntity<String> response = doDelete(
                    EVAL_BASE + "/" + fx.evaluationB().getPublicUuid() + "/rubric",
                    loginA.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Admin A attaches a rubric on B's evaluation publicUuid → 404")
        void crossTenantAttachOnEvaluationIs404() throws Exception {
            Fixture fx = setupTenants();
            AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

            String body = "{\"rubricPublicUuid\":\""
                    + fx.rubricA().getPublicUuid() + "\"}";
            ResponseEntity<String> response = doPost(
                    EVAL_BASE + "/" + fx.evaluationB().getPublicUuid() + "/rubric",
                    loginA.accessToken(), body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("After A attaches its rubric, B does NOT see the link "
                + "in their own gradebook-style listing")
        void linkIsScoped() {
            Fixture fx = setupTenantsWithLink();

            // Probe the link table under B's tenant — it should not see A's link.
            List<UUID> bLinkEvalIds = TenantContext.runAs(fx.tenantB().getId(),
                    () -> tx().execute(s -> linkRepository.findAll().stream()
                            .map(EvaluationRubric::getEvaluation)
                            .map(Evaluation::getPublicUuid)
                            .toList()));

            assertThat(bLinkEvalIds)
                    .as("tenant B should not see A's link to A's evaluation")
                    .doesNotContain(fx.evaluationA().getPublicUuid());
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
            Rubric rubricA, Rubric rubricB
    ) {
    }

    private Fixture setupTenants() {
        Tenant tenantA = createTenant("it-erlink-a-");
        Tenant tenantB = createTenant("it-erlink-b-");
        createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
        createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
        seedAcademicCatalog(tenantA);
        seedAcademicCatalog(tenantB);

        Bundle bundleA = seedEvaluationAndRubric(tenantA);
        Bundle bundleB = seedEvaluationAndRubric(tenantB);

        return new Fixture(tenantA, tenantB,
                bundleA.evaluation(), bundleB.evaluation(),
                bundleA.rubric(), bundleB.rubric());
    }

    private Fixture setupTenantsWithLink() {
        Fixture fx = setupTenants();
        // Pre-attach A's rubric to A's evaluation, and B's to B's, so the
        // cross-tenant detach / get tests have something to fail on.
        attachInTenant(fx.tenantA(), fx.evaluationA(), fx.rubricA());
        attachInTenant(fx.tenantB(), fx.evaluationB(), fx.rubricB());
        return fx;
    }

    private void attachInTenant(Tenant tenant, Evaluation evaluation, Rubric rubric) {
        TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
            EvaluationRubric link = new EvaluationRubric();
            link.setEvaluation(evaluation);
            link.setRubric(rubric);
            return linkRepository.saveAndFlush(link);
        }));
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

    record Bundle(Evaluation evaluation, Rubric rubric) {
    }

    private Bundle seedEvaluationAndRubric(Tenant tenant) {
        return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
            AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
                    .orElseThrow();
            Grade grade = gradeRepository
                    .findAllByLevelOrderByOrdinalAsc(primaria).get(0);

            AcademicYear year = new AcademicYear();
            year.setName("2026-IT-ERLINK");
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
            course.setCode("MAT_IT_ER");
            course.setName("Matemática IT EvalRubric");
            course.setIsActive(true);
            Course savedCourse = courseRepository.saveAndFlush(course);

            CourseLevel courseLevel = new CourseLevel();
            courseLevel.setCourse(savedCourse);
            courseLevel.setLevel(primaria);
            courseLevelRepository.saveAndFlush(courseLevel);

            Teacher teacher = new Teacher();
            teacher.setFirstName("María");
            teacher.setLastName("García");
            teacher.setDocumentType(DocumentType.DNI);
            teacher.setDocumentNumber("87655555"
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

            Evaluation evaluation = new Evaluation();
            evaluation.setTeacherAssignment(savedAssignment);
            evaluation.setKind(EvaluationKind.RUBRIC);
            evaluation.setName("Rúbrica de presentación");
            evaluation.setWeight(BigDecimal.valueOf(1.00));
            evaluation.setScheduledDate(LocalDate.of(2026, 4, 10));
            evaluation.setScale(EvaluationScale.LITERAL_AD);
            evaluation.setStatus(EvaluationStatus.DRAFT);
            evaluation.setIsActive(Boolean.TRUE);
            Evaluation savedEvaluation = evaluationRepository.saveAndFlush(evaluation);

            Rubric rubric = new Rubric();
            rubric.setName("Rúbrica IT-ERLINK " + tenant.getSlug());
            rubric.setDescription("Para test multi-tenant");
            rubric.setIsSystem(Boolean.FALSE);
            rubric.setIsActive(Boolean.TRUE);
            rubric.setCriteria(List.of(Map.of(
                    "key", "redaccion",
                    "name", "Redacción",
                    "weight", BigDecimal.valueOf(100),
                    "descriptors", List.of())));
            rubric.setLevels(List.of(
                    Map.of("code", "EN_INICIO",   "name", "En inicio",   "order", 1),
                    Map.of("code", "EN_PROCESO",  "name", "En proceso",  "order", 2),
                    Map.of("code", "ESPERADO",    "name", "Esperado",    "order", 3),
                    Map.of("code", "SOBRESALIENTE","name", "Sobresaliente","order", 4)));
            Rubric savedRubric = rubricRepository.saveAndFlush(rubric);

            return new Bundle(savedEvaluation, savedRubric);
        }));
    }
}
