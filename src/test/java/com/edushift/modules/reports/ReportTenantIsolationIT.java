package com.edushift.modules.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.modules.reports.repository.ReportJobRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Cross-tenant isolation IT for reports (BE-13.1, Sprint 13).
 *
 * <p>Validates that a user authenticated in tenant B can never read, list,
 * or download a {@link ReportJob} owned by tenant A. The expected outcome
 * for direct-by-id lookups is 404 (anti-enumeration), and A's job must
 * not appear in tenant B's listing.</p>
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>GET /reports/{A's uuid} as B → 404.</li>
 *   <li>GET /reports/{A's uuid}/download as B → 404.</li>
 *   <li>GET /reports as B → A's job NOT in payload.</li>
 * </ul>
 */
@DisplayName("Reports multi-tenancy isolation (BE-13.1)")
class ReportTenantIsolationIT extends IntegrationTest {

    private static final String REPORTS_BASE = "/v1/reports";
    private static final String AUTH_BASE = "/v1/auth";

    private static final String SHARED_EMAIL = "user@report-isolation.test";
    private static final String PASSWORD_A = "PassReportA-1!";
    private static final String PASSWORD_B = "PassReportB-2!";

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReportJobRepository reportJobRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        if (tx == null) tx = new TransactionTemplate(txManager);
    }

    // ============================================================ fixture

    private record Fixture(Tenant tenantA, Tenant tenantB, User userA, User userB, ReportJob jobA) {}

    private Fixture setupTenants() {
        // DEBT-FK-BUGS-2 / cleanup: slugs suffixed with UUID so each @Test
        // gets fresh tenants. The shared static PostgreSQLContainer is JVM-
        // scoped, so multiple @Test methods in the same class would
        // otherwise collide on the unique slug constraint.
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        Tenant tenantA = createTenant("report-iso-a-" + suffix);
        Tenant tenantB = createTenant("report-iso-b-" + suffix);
        User userA = createUserIn(tenantA, "userA-" + suffix, SHARED_EMAIL, PASSWORD_A);
        User userB = createUserIn(tenantB, "userB-" + suffix, SHARED_EMAIL, PASSWORD_B);
        ReportJob jobA = createReportJobIn(tenantA, userA);
        return new Fixture(tenantA, tenantB, userA, userB, jobA);
    }

    private Tenant createTenant(String slug) {
        return tx.execute(s -> {
            Tenant t = new Tenant();
            t.setPublicUuid(UUID.randomUUID());
            t.setSlug(slug);
            t.setName("Report-iso " + slug);
            t.setStatus(TenantStatus.ACTIVE);
            return tenantRepository.save(t);
        });
    }

    private User createUserIn(Tenant t, String username, String email, String rawPwd) {
        return tx.execute(s -> {
            User u = new User();
            u.setPublicUuid(UUID.randomUUID());
            u.setTenantId(t.getId());
            u.setFirstName(username);
            u.setLastName("of-" + t.getSlug());
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(rawPwd));
            u.setStatus(UserStatus.ACTIVE);
            u.setRoles(new String[] { UserRole.TENANT_ADMIN.name() });
            return userRepository.save(u);
        });
    }

    private ReportJob createReportJobIn(Tenant t, User requester) {
        return TenantContext.runAs(t.getId(), () -> tx.execute(s -> {
            ReportJob j = new ReportJob();
            j.setPublicUuid(UUID.randomUUID());
            j.setTenantId(t.getId());
            j.setRequestedByUserId(requester.getPublicUuid());
            j.setReportType(ReportType.GRADE_BOOK);
            j.setFormat(Format.PDF);
            j.setParams("{\"sectionId\":null}");
            j.setStatus(ReportJob.Status.PENDING);
            return reportJobRepository.save(j);
        }));
    }

    // ============================================================ tests

    @Nested
    @DisplayName("Cross-tenant reads")
    class CrossTenantReads {

        @Test
        @DisplayName("GET /reports/{A's uuid} as B → 404 (anti-enumeration)")
        void getByPublicUuidReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    REPORTS_BASE + "/" + f.jobA().getPublicUuid(),
                    loginB.accessToken());

            assertThat(response.getStatusCode())
                    .as("cross-tenant GET on a report job must be 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /reports/{A's uuid}/download as B → 404 (anti-enumeration)")
        void downloadByPublicUuidReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    REPORTS_BASE + "/" + f.jobA().getPublicUuid() + "/download",
                    loginB.accessToken());

            assertThat(response.getStatusCode())
                    .as("cross-tenant download of a report job must be 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /reports as B → A's job NOT in payload (no leak in listing)")
        void listDoesNotLeak() throws Exception {
            Fixture f = setupTenants();
            // Seed a job for B as well so the page isn't trivially empty.
            createReportJobIn(f.tenantB(), f.userB());

            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    REPORTS_BASE + "?page=0&size=20",
                    loginB.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = objectMapper.readTree(response.getBody());
            // DEBT-FK-BUGS-2 / parseo: el ReportController#list devuelve
            // ApiResponse<List<ReportJobResponse>> con shape {data: [...],
            // meta: {...}} (no la forma {data: {content: [...]}} que asume
            // el test original — esa forma es la de Spring Page<>, no la
            // de ApiResponse.ok(list, Meta.of(page))).
            JsonNode items = body.has("data") ? body.get("data") : body;
            assertThat(items)
                    .as("tenant B list response should not be empty (we seeded one)")
                    .isNotNull();
            for (JsonNode item : items) {
                String id = item.get("publicUuid").asText();
                assertThat(id)
                        .as("tenant A report publicUuid leaked into B's listing")
                        .isNotEqualTo(f.jobA().getPublicUuid().toString());
            }
        }
    }

    @Nested
    @DisplayName("Cross-tenant idempotency")
    class CrossTenantIdempotency {

        @Test
        @DisplayName("idempotency key 'same-key' for the same logical user in different tenants "
                + "does NOT return the other tenant's job (tenantId is part of the key)")
        void idemKeyIsolatedByTenant() {
            Fixture f = setupTenants();
            String idemKey = "shared-key-" + UUID.randomUUID();

            // Tenant A creates a job with idemKey
            AuthResponse loginA = login(f.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
            ResponseEntity<String> createA = doPost(REPORTS_BASE, loginA.accessToken(),
                    "{\"reportType\":\"GRADE_BOOK\",\"format\":\"PDF\",\"params\":\"{}\",\"idemKey\":\""
                            + idemKey + "\"}");
            assertThat(createA.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Tenant B uses the SAME idemKey — must create a NEW job, not return A's
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> createB = doPost(REPORTS_BASE, loginB.accessToken(),
                    "{\"reportType\":\"GRADE_BOOK\",\"format\":\"PDF\",\"params\":\"{}\",\"idemKey\":\""
                            + idemKey + "\"}");
            assertThat(createB.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Different publicUuids: A's request returned A's uuid, B's returned B's.
            try {
                String bodyA = createA.getBody();
                String bodyB = createB.getBody();
                String uuidA = objectMapper.readTree(bodyA).at("/data/publicUuid").asText();
                String uuidB = objectMapper.readTree(bodyB).at("/data/publicUuid").asText();
                assertThat(uuidA)
                        .as("idempotency key reused across tenants must yield two different jobs")
                        .isNotEqualTo(uuidB);
            }
            catch (Exception e) {
                throw new AssertionError("Could not parse create response", e);
            }
        }
    }

    // ============================================================ helpers

    private AuthResponse login(String tenantSlug, String email, String pwd) {
        // DEBT-FK-BUGS-2 / login: AuthController#login reads the tenant slug
        // from the X-Tenant-Slug HEADER, not from the body. The original test
        // (commit 3d5c295) set tenantSlug in the body, so the controller's
        // @RequestHeader(value = TENANT_SLUG_HEADER, required = true) threw
        // MissingRequestHeaderException → 400 BAD_REQUEST. This fix adds the
        // header that the controller actually expects.
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, pwd);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Slug", tenantSlug);
        ResponseEntity<AuthResponse> response = rest.exchange(
                AUTH_BASE + "/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> doGet(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> doPost(String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
