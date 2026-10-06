package com.edushift.modules.sessions.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * TEACHER access to {@code /v1/learning-sessions} (DEBT-SES-2).
 *
 * <p>Teachers may list and create sessions; the service scopes results to
 * their own {@code teacherAssignment} rows.</p>
 */
@DisplayName("Learning session teacher scope (DEBT-SES-2)")
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
    @Autowired private PlatformPlanRepository planRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    @Nested
    @DisplayName("F2 — TEACHER can list own sessions")
    class TeacherCanList {

        @Test
        @DisplayName("GET /learning-sessions as TEACHER -> 200")
        void teacherListIs200() throws Exception {
            Tenant tenant = setupTenant();
            AuthResponse teacher = login(tenant.getSlug(), TEACHER_EMAIL, TEACHER_PWD);

            ResponseEntity<String> r = doGet(LS_BASE, teacher.accessToken());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("F3 — TEACHER can POST (validation may follow)")
    class TeacherCanCreate {

        @Test
        @DisplayName("POST /learning-sessions as TEACHER is not 403")
        void teacherCreateIsNotForbidden() throws Exception {
            Tenant tenant = setupTenant();
            AuthResponse teacher = login(tenant.getSlug(), TEACHER_EMAIL, TEACHER_PWD);

            String body = String.format(
                    "{\"assignmentUuid\":\"%s\",\"unitUuid\":\"%s\",\"title\":\"x\","
                            + "\"scheduledDate\":\"2026-04-10\",\"durationMinutes\":45}",
                    UUID.randomUUID(), UUID.randomUUID());
            ResponseEntity<String> r = doPost(LS_BASE, teacher.accessToken(), body);
            assertThat(r.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("Control — TENANT_ADMIN still works")
    class AdminCanList {

        @Test
        @DisplayName("GET /learning-sessions as TENANT_ADMIN -> 200")
        void adminListIs200() throws Exception {
            Tenant tenant = setupTenant();
            AuthResponse admin = login(tenant.getSlug(), ADMIN_EMAIL, ADMIN_PWD);
            ResponseEntity<String> r = doGet(LS_BASE + "?size=10", admin.accessToken());
            assertThat(r.getStatusCode()).as("body=%s", r.getBody()).isEqualTo(HttpStatus.OK);
        }
    }

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

    private Tenant setupTenant() {
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
        return saved;
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
