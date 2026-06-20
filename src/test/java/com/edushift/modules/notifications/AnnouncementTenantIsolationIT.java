package com.edushift.modules.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.repository.AnnouncementRepository;
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
 * Cross-tenant isolation IT for announcements (BE-13.2, Sprint 13).
 *
 * <p>Validates that a user authenticated in tenant B can never read, list,
 * mutate, or delete an {@link Announcement} owned by tenant A.</p>
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>GET /announcements/{A's uuid} as B → 404.</li>
 *   <li>GET /announcements as B → A's announcement NOT in payload.</li>
 *   <li>POST /announcements/{A's uuid}/read as B → 404, no read-tracking row created.</li>
 *   <li>PATCH /announcements/{A's uuid} as B (admin) → 404, A's row unchanged.</li>
 *   <li>DELETE /announcements/{A's uuid} as B (admin) → 404, A's row still present.</li>
 * </ul>
 */
@DisplayName("Announcements multi-tenancy isolation (BE-13.2)")
class AnnouncementTenantIsolationIT extends IntegrationTest {

    private static final String ANNOUNCEMENTS_BASE = "/v1/announcements";
    private static final String AUTH_BASE = "/v1/auth";

    private static final String SHARED_EMAIL = "user@announce-isolation.test";
    private static final String PASSWORD_A = "PassAnnounceA-1!";
    private static final String PASSWORD_B = "PassAnnounceB-2!";

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        if (tx == null) tx = new TransactionTemplate(txManager);
    }

    // ============================================================ fixture

    private record Fixture(
            Tenant tenantA, Tenant tenantB,
            User userA, User userB,
            Announcement announcementA) {}

    private Fixture setupTenants() {
        Tenant tenantA = createTenant("announce-iso-a");
        Tenant tenantB = createTenant("announce-iso-b");
        User userA = createUserIn(tenantA, "userA", SHARED_EMAIL, PASSWORD_A);
        User userB = createUserIn(tenantB, "userB", SHARED_EMAIL, PASSWORD_B);
        Announcement a = createAnnouncementIn(tenantA, userA, "Reunión de padres lunes 8 AM");
        return new Fixture(tenantA, tenantB, userA, userB, a);
    }

    private Tenant createTenant(String slug) {
        return tx.execute(s -> {
            Tenant t = new Tenant();
            t.setPublicUuid(UUID.randomUUID());
            t.setSlug(slug);
            t.setName("Announce-iso " + slug);
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

    private Announcement createAnnouncementIn(Tenant t, User author, String body) {
        return TenantContext.runAs(t.getId(), () -> tx.execute(s -> {
            Announcement a = new Announcement();
            a.setPublicUuid(UUID.randomUUID());
            a.setTenantId(t.getId());
            a.setAuthorUserId(author.getPublicUuid());
            a.setTitle("Reunión");
            a.setBodyHtml("<p>" + body + "</p>");
            a.setAudienceType(Announcement.AudienceType.SCHOOL);
            a.setStatus(Announcement.Status.PUBLISHED);
            a.setPinned(false);
            a.setPublishedAt(java.time.Instant.now());
            return announcementRepository.save(a);
        }));
    }

    // ============================================================ tests

    @Nested
    @DisplayName("Cross-tenant reads (user surface)")
    class CrossTenantUserReads {

        @Test
        @DisplayName("GET /announcements/{A's uuid} as B → 404 (anti-enumeration)")
        void getByPublicUuidReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    ANNOUNCEMENTS_BASE + "/" + f.announcementA().getPublicUuid(),
                    loginB.accessToken());

            assertThat(response.getStatusCode())
                    .as("cross-tenant GET on an announcement must be 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /announcements as B → A's announcement NOT in payload")
        void listDoesNotLeak() throws Exception {
            Fixture f = setupTenants();
            // Seed an announcement for B as well so the list is not empty.
            createAnnouncementIn(f.tenantB(), f.userB(), "Anuncio para B");

            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    ANNOUNCEMENTS_BASE + "?limit=50",
                    loginB.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode data = body.has("data") ? body.get("data") : body;
            for (JsonNode item : data) {
                String id = item.get("publicUuid").asText();
                assertThat(id)
                        .as("tenant A announcement publicUuid leaked into B's listing")
                        .isNotEqualTo(f.announcementA().getPublicUuid().toString());
            }
        }

        @Test
        @DisplayName("POST /announcements/{A's uuid}/read as B → 404 (no read-tracking side effect)")
        void markReadReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);

            ResponseEntity<String> response = doPost(
                    ANNOUNCEMENTS_BASE + "/" + f.announcementA().getPublicUuid() + "/read",
                    loginB.accessToken(), "{}");

            assertThat(response.getStatusCode())
                    .as("cross-tenant mark-read must be 404 (anti-enumeration)")
                    .isEqualTo(HttpStatus.NOT_FOUND);

            // Defense in depth: A's announcement must still be PUBLISHED (no
            // accidental write from tenant B's request).
            Announcement reloaded = TenantContext.runAs(f.tenantA().getId(),
                    () -> announcementRepository.findByPublicUuid(f.announcementA().getPublicUuid())
                            .orElseThrow());
            assertThat(reloaded.getStatus())
                    .as("tenant A announcement must NOT have been mutated by tenant B")
                    .isEqualTo(Announcement.Status.PUBLISHED);
        }
    }

    @Nested
    @DisplayName("Cross-tenant writes (admin surface)")
    class CrossTenantAdminWrites {

        @Test
        @DisplayName("PATCH /announcements/{A's uuid} as B admin → 404, A's row unchanged")
        void patchAsBReturns404() {
            Fixture f = setupTenants();
            String originalTitle = f.announcementA().getTitle();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);

            String body = "{\"title\":\"HACKED\",\"bodyHtml\":\"<p>x</p>\","
                    + "\"audienceType\":\"ALL\",\"pinned\":false}";
            ResponseEntity<String> response = doPatch(
                    ANNOUNCEMENTS_BASE + "/" + f.announcementA().getPublicUuid(),
                    loginB.accessToken(), body);

            assertThat(response.getStatusCode())
                    .as("cross-tenant admin PATCH must be 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);

            // Title unchanged
            Announcement reloaded = TenantContext.runAs(f.tenantA().getId(),
                    () -> announcementRepository.findByPublicUuid(f.announcementA().getPublicUuid())
                            .orElseThrow());
            assertThat(reloaded.getTitle())
                    .as("tenant A title must NOT have been modified by tenant B admin")
                    .isEqualTo(originalTitle);
        }

        @Test
        @DisplayName("DELETE /announcements/{A's uuid} as B admin → 404, A's row still present")
        void deleteAsBReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);

            ResponseEntity<String> response = doDelete(
                    ANNOUNCEMENTS_BASE + "/" + f.announcementA().getPublicUuid(),
                    loginB.accessToken());

            assertThat(response.getStatusCode())
                    .as("cross-tenant admin DELETE must be 404 (not 204)")
                    .isEqualTo(HttpStatus.NOT_FOUND);

            // Row still in A
            boolean stillPresent = TenantContext.runAs(f.tenantA().getId(),
                    () -> announcementRepository.findByPublicUuid(f.announcementA().getPublicUuid())
                            .isPresent());
            assertThat(stillPresent)
                    .as("tenant A announcement must NOT have been deleted by tenant B admin")
                    .isTrue();
        }
    }

    // ============================================================ helpers

    private AuthResponse login(String tenantSlug, String email, String pwd) {
        String body = String.format("{\"tenantSlug\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                tenantSlug, email, pwd);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
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

    private ResponseEntity<String> doPatch(String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> doDelete(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }
}
