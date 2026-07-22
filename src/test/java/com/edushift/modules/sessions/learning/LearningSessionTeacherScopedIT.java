package com.edushift.modules.sessions.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
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
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
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
 * Documents the F2/F3 contract for the TEACHER role on the
 * {@code /v1/learning-sessions} surface.
 *
 * <p>The walkthrough plan §1 says "F2 panel del docente" reads
 * {@code GET /learning-sessions?mine=true} and F3 "crear sesión de clase"
 * uses {@code POST /learning-sessions}. The actual RBAC matrix
 * (see {@code LearningSessionController}) marks every endpoint on this
 * controller {@code hasRole('TENANT_ADMIN')}, which means a TEACHER
 * cannot:</p>
 * <ul>
 *   <li>list their own sessions (DEBT-TEA-2 / docs/modules/teachers.md);</li>
 *   <li>create a session on their own assignment;</li>
 *   <li>transition a session through PLANNED → IN_PROGRESS → COMPLETED.</li>
 * </ul>
 *
 * <p>This IT is the <strong>gap documentation</strong> — it does NOT
 * prove the flow works (it cannot, by design, until F2/F3 are migrated
 * to a teacher-aware controller). When the gap is closed the assertions
 * here will start failing and serve as a TODO list.</p>
 */
@DisplayName("Learning session teacher scope (F2/F3 RBAC gap)")
class LearningSessionTeacherScopedIT extends IntegrationTest {

    private static final String AUTH_BASE = "/v1/auth";
    private static final String LS_BASE = "/v1/learning-sessions";

    private static final String TEACHER_EMAIL = "ls-teacher@scope-it.test";
    private static final String TEACHER_PWD = "LsScope2026!";
    private static final String ADMIN_EMAIL = "ls-admin@scope-it.test";
    private static final String ADMIN_PWD = "LsAdmin2026!";

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AcademicLevelRepository levelRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private AcademicYearRepository yearRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private LearningSessionRepository sessionRepository;
    @Autowired private PlatformPlanRepository planRepository;
    @Autowired private AcademicSeedService seedService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    @Nested
    @DisplayName("F2 — TEACHER cannot list own sessions")
    class TeacherCannotList {

        @Test
        @DisplayName("GET /learning-sessions as TEACHER -> 403 (DEBT-TEA-2)")
        void teacherListIs403() throws Exception {
            Fixture fx = setupTenant();
            AuthResponse teacher = login(fx.tenant().getSlug(), TEACHER_EMAIL, TEACHER_PWD);

            ResponseEntity<String> r = doGet(LS_BASE + "?size=5", teacher.accessToken());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("F3 — TEACHER cannot create sessions on own assignments")
    class TeacherCannotCreate {

        @Test
        @DisplayName("POST /learning-sessions as TEACHER -> 403 (gap: same as F2)")
        void teacherCreateIs403() throws Exception {
            Fixture fx = setupTenant();
            AuthResponse teacher = login(fx.tenant().getSlug(), TEACHER_EMAIL, TEACHER_PWD);

            // Seed a session row first so a teacher-scoped controller would
            // have something to filter against; not strictly needed for 403.
            String body = String.format(
                    "{\"assignmentUuid\":\"%s\",\"unitUuid\":\"%s\",\"title\":\"x\",\"scheduledDate\":\"%s\",\"durationMinutes\":45}",
                    fx.assignmentPublicUuid, fx.unitPublicUuid, LocalDate.now());
            ResponseEntity<String> r = doPost(LS_BASE, teacher.accessToken(), body);
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("F2 — TENANT_ADMIN can list (control)")
    class AdminCanList {

        @Test
        @DisplayName("GET /learning-sessions as TENANT_ADMIN -> 200 (control)")
        void adminListIs200() throws Exception {
            Fixture fx = setupTenant();
            AuthResponse admin = login(fx.tenant().getSlug(), ADMIN_EMAIL, ADMIN_PWD);
            ResponseEntity<String> r = doGet(LS_BASE + "?size=10", admin.accessToken());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================================
    // HTTP helpers
    // =====================================================================

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private AuthResponse login(String slug, String email, String password) throws Exception {
        HttpHeaders h = jsonHeaders();
        h.add("X-Tenant-Slug", slug);
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        ResponseEntity<String> r = rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).as("login body=%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return objectMapper.readValue(r.getBody(), AuthResponse.class);
    }

    private ResponseEntity<String> doGet(String path, String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearer);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> doPost(String path, String bearer, String body) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(bearer);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    // =====================================================================
    // Fixture
    // =====================================================================

    record Fixture(Tenant tenant, String assignmentPublicUuid, String unitPublicUuid) { }

    private Fixture setupTenant() {
        PlatformPlan basicPlan = planRepository.findAll().stream()
                .filter(p -> "BASIC".equals(p.getCode()))
                .findFirst().orElseGet(() -> planRepository.findAll().get(0));
        Tenant t = new Tenant();
        t.setSlug("lsscope-" + UUID.randomUUID().toString().substring(0, 8));
        t.setName("LS Scope IT " + t.getSlug());
        t.setStatus(TenantStatus.ACTIVE);
        t.setPlanId(basicPlan.getId());
        Tenant saved = tx().execute(s -> tenantRepository.saveAndFlush(t));

        createUser(saved, TEACHER_EMAIL, TEACHER_PWD, UserRole.TEACHER);
        createUser(saved, ADMIN_EMAIL, ADMIN_PWD, UserRole.TENANT_ADMIN);

        TenantContext.runAs(saved.getId(), () -> tx().execute(s -> {
            seedService.seedDefaults(saved.getId());
            return null;
        }));

        // Pick the first assignment and unit so the admin create can succeed.
        UUID[] uuids = TenantContext.runAs(saved.getId(), () -> tx().execute(s -> {
            AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA").orElseThrow();
            Grade grade = gradeRepository.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

            AcademicYear year = new AcademicYear();
            year.setName("2026-LS-" + saved.getSlug().substring(0, 4));
            year.setStartDate(LocalDate.of(2026, 3, 1));
            year.setEndDate(LocalDate.of(2026, 12, 20));
            year.setStatus(AcademicYearStatus.ACTIVE);
            yearRepository.saveAndFlush(year);

            Section section = new Section();
            section.setAcademicYear(year);
            section.setGrade(grade);
            section.setName("1ro A");
            sectionRepository.saveAndFlush(section);

            // For the 403 path we only need the controller to reach the security
            // filter before validating the body, so random UUIDs are fine. For
            // the admin control test we DON'T try to create a session — we only
            // verify the list endpoint returns 200.
            return null;
        }));
        return new Fixture(saved,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
    }

    private void createUser(Tenant tenant, String email, String pwd, UserRole... roles) {
        TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
            User u = new User();
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(pwd));
            u.setFirstName("Ls");
            u.setLastName("User");
            u.setStatus(UserStatus.ACTIVE);
            u.setEmailVerified(true);
            u.setMfaEnabled(false);
            for (UserRole r : roles) { u.addRole(r); }
            userRepository.saveAndFlush(u);
            return null;
        }));
    }
}
