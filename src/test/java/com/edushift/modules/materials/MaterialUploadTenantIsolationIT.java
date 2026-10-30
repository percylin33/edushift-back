package com.edushift.modules.materials;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Cross-tenant coverage for the <strong>multipart upload</strong> path of
 * {@code POST /v1/sections/{sectionPublicUuid}/materials}.
 *
 * <p>{@link MaterialTenantIsolationIT} already covers the JSON
 * {@code createLink} path and the read/delete paths. This IT closes the
 * gap for the binary upload path which uses a different content-type
 * negotiation and a multipart parser, and therefore could regress
 * independently (e.g. the multipart resolver might bypass the section
 * tenant-scope check).</p>
 */
@DisplayName("Materials upload tenant isolation (BE-7a.5)")
class MaterialUploadTenantIsolationIT extends IntegrationTest {

    private static final String AUTH_BASE = "/v1/auth";
    private static final String UPLOAD_PATH = "/v1/sections/{sectionUuid}/materials";

    private static final String ADMIN_A = "matup-a@iso-it.test";
    private static final String ADMIN_B = "matup-b@iso-it.test";
    private static final String PWD = "MatUp2026!";

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AcademicLevelRepository levelRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private AcademicYearRepository yearRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private AcademicSeedService seedService;
    @Autowired private PlatformPlanRepository planRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private AcademicPeriodRepository periodRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private TeacherAssignmentRepository assignmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    @Nested
    @DisplayName("Cross-tenant multipart upload")
    class CrossTenantUpload {

        @Test
        @DisplayName("Tenant B uploading to tenant A's section -> 404 RESOURCE_NOT_FOUND")
        void foreignUploadIs404() throws Exception {
            Fixture a = setupTenant("a-");
            Fixture b = setupTenant("b-");
            AuthResponse adminB = login(b.tenant().getSlug(), ADMIN_B, PWD);

            String path = UPLOAD_PATH.replace("{sectionUuid}", a.section().getPublicUuid().toString());
            ResponseEntity<String> r = multipartPost(
                    path, adminB.accessToken(), "foreign upload", a.assignment().getPublicUuid());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Tenant A uploading to its own section -> 201 Created (control)")
        void ownUploadIs201() throws Exception {
            Fixture a = setupTenant("c-");
            AuthResponse adminA = login(a.tenant().getSlug(), ADMIN_A, PWD);

            String path = UPLOAD_PATH.replace("{sectionUuid}", a.section().getPublicUuid().toString());
            ResponseEntity<String> r = multipartPost(
                    path, adminA.accessToken(), "own upload", a.assignment().getPublicUuid());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("Upload with completely fake section uuid -> 404 RESOURCE_NOT_FOUND (anti-enumeration)")
        void fakeSectionUuidIs404() throws Exception {
            Fixture b = setupTenant("d-");
            AuthResponse adminB = login(b.tenant().getSlug(), ADMIN_B, PWD);

            String path = UPLOAD_PATH.replace("{sectionUuid}", UUID.randomUUID().toString());
            ResponseEntity<String> r = multipartPost(path, adminB.accessToken(), "fake", UUID.randomUUID());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Build a multipart/form-data POST manually so the Content-Type
     * {@code boundary=...} parameter is set correctly. TestRestTemplate +
     * MultiValueMap doesn't always auto-add the boundary, which makes the
     * controller reject the request as 415 UNSUPPORTED_MEDIA_TYPE.
     */
    private ResponseEntity<String> multipartPost(
            String path, String bearer, String title, UUID assignmentPublicUuid) throws Exception {
        String boundary = "----EduShiftBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = tinyPdf();
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"tiny.pdf\"\r\n");
        sb.append("Content-Type: application/pdf\r\n\r\n");
        sb.append(new String(fileBytes, "ISO-8859-1"));
        sb.append("\r\n--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"metadata\"\r\n");
        sb.append("Content-Type: application/json\r\n\r\n");
        sb.append("{\"title\":\"").append(title)
                .append("\",\"description\":\"x\",\"teacherAssignmentPublicUuid\":\"")
                .append(assignmentPublicUuid).append("\"}");
        sb.append("\r\n--").append(boundary).append("--\r\n");
        byte[] body = sb.toString().getBytes("ISO-8859-1");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
        h.setBearerAuth(bearer);
        h.setContentLength(body.length);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private byte[] tinyPdf() {
        // Minimal "PDF-like" byte stream — the metadata parser doesn't care
        // about MIME type, and we don't want to pull in a PDF library for a
        // tenant-isolation test.
        return "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n1 0 obj<<>>endobj\nxref\n0 1\n0000000000 65535 f\ntrailer<<>>\nstartxref\n0\n%%EOF".getBytes();
    }

    private AuthResponse login(String slug, String email, String password) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Tenant-Slug", slug);
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        ResponseEntity<String> r = rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).as("login body=%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return readSessionResponse(objectMapper, r);
    }

    // =====================================================================
    // Fixture
    // =====================================================================

    record Fixture(Tenant tenant, Section section, TeacherAssignment assignment) { }
    record AcademicFixture(Section section, TeacherAssignment assignment) { }

    private Fixture setupTenant(String slugPrefix) {
        PlatformPlan basicPlan = planRepository.findAll().stream()
                .filter(p -> "BASIC".equals(p.getCode()))
                .findFirst().orElseGet(() -> planRepository.findAll().get(0));
        Tenant t = newTestTenant();
        t.setSlug("matup" + slugPrefix + UUID.randomUUID().toString().substring(0, 8));
        t.setName("MatUp IT " + t.getSlug());
        t.setStatus(TenantStatus.ACTIVE);
        t.setPlanId(basicPlan.getId());
        Tenant saved = tx().execute(s -> tenantRepository.saveAndFlush(t));

        createAdmin(saved, ADMIN_A, PWD);
        createAdmin(saved, ADMIN_B, PWD);

        TenantContext.runAs(saved.getId(), () -> tx().execute(s -> {
            seedService.seedDefaults(saved.getId());
            return null;
        }));

        AcademicFixture academicFixture = TenantContext.runAs(saved.getId(), () -> tx().execute(s -> {
            AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA").orElseThrow();
            Grade grade = gradeRepository.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

            AcademicYear year = new AcademicYear();
            year.setName("2026-MAT-" + saved.getSlug().substring(0, 4));
            year.setStartDate(LocalDate.of(2026, 3, 1));
            year.setEndDate(LocalDate.of(2026, 12, 20));
            year.setStatus(AcademicYearStatus.ACTIVE);
            AcademicYear savedYear = yearRepository.saveAndFlush(year);

            Section sec = new Section();
            sec.setAcademicYear(savedYear);
            sec.setGrade(grade);
            sec.setName("1ro A");
            Section savedSection = sectionRepository.saveAndFlush(sec);

            AcademicPeriod period = new AcademicPeriod();
            period.setAcademicYear(savedYear);
            period.setPeriodType(PeriodType.ANUAL);
            period.setOrdinal(1);
            period.setName("Annual");
            period.setStartDate(savedYear.getStartDate());
            period.setEndDate(savedYear.getEndDate());
            AcademicPeriod savedPeriod = periodRepository.saveAndFlush(period);

            Course course = new Course();
            course.setCode("MATUP_" + saved.getId().toString().substring(0, 8).toUpperCase());
            course.setName("Materials upload fixture course");
            Course savedCourse = courseRepository.saveAndFlush(course);

            Teacher teacher = new Teacher();
            teacher.setDocumentType(DocumentType.DNI);
            teacher.setDocumentNumber("MU-" + UUID.randomUUID().toString().substring(0, 8));
            teacher.setFirstName("Upload");
            teacher.setLastName("Fixture");
            teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);
            Teacher savedTeacher = teacherRepository.saveAndFlush(teacher);

            TeacherAssignment assignment = new TeacherAssignment();
            assignment.setTeacher(savedTeacher);
            assignment.setSection(savedSection);
            assignment.setCourse(savedCourse);
            assignment.setAcademicPeriod(savedPeriod);
            return new AcademicFixture(savedSection, assignmentRepository.saveAndFlush(assignment));
        }));

        return new Fixture(saved, academicFixture.section(), academicFixture.assignment());
    }

    private void createAdmin(Tenant tenant, String email, String pwd) {
        TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
            User u = new User();
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(pwd));
            u.setFirstName("MatUp");
            u.setLastName("Admin");
            u.setStatus(UserStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setMfaEnabled(false);
            u.addRole(UserRole.TENANT_ADMIN);
            userRepository.saveAndFlush(u);
            return null;
        }));
    }
}
