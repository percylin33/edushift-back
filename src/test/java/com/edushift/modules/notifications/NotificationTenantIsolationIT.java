package com.edushift.modules.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * Cross-tenant isolation IT for notifications (Sprint 10 / BE-10.6, DEBT-9-3).
 *
 * <p>Validates that a user authenticated in tenant B can never
 * see, mutate, or list a {@link Notification} owned by tenant A.
 * We rely on Hibernate's {@code @TenantId} auto-filter; the
 * expected outcome is 404 (anti-enumeration), never 403.</p>
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>GET /notifications/{A's uuid} as B → 404.</li>
 *   <li>GET /notifications as B → A's notification not in payload.</li>
 *   <li>POST /notifications/{A's uuid}/read as B → 404, A's row still unread.</li>
 *   <li>DEBT-9-3 specifically: a notification for student X in tenant A
 *       does not appear when a guardian in tenant B queries their own list.</li>
 * </ul>
 */
@DisplayName("Notifications multi-tenancy isolation (DEBT-9-3)")
class NotificationTenantIsolationIT extends IntegrationTest {

    private static final String NOTIFICATIONS_BASE = "/v1/notifications";
    private static final String AUTH_BASE = "/v1/auth";

    private static final String SHARED_EMAIL = "user@notif-isolation.test";
    private static final String PASSWORD_A = "PassNotifA-1!";
    private static final String PASSWORD_B = "PassNotifB-2!";

    @Autowired private TestRestTemplate rest;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ObjectMapper objectMapper;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        if (tx == null) tx = new TransactionTemplate(txManager);
    }

    private TransactionTemplate tx() {
        return tx;
    }

    // ============================================================ fixture

    private record Fixture(Tenant tenantA, Tenant tenantB, User userA, User userB, Notification notifA) {}

    private Fixture setupTenants() {
        Tenant tenantA = createTenant("notif-iso-a");
        Tenant tenantB = createTenant("notif-iso-b");
        User userA = createUserIn(tenantA, "userA", SHARED_EMAIL, PASSWORD_A);
        User userB = createUserIn(tenantB, "userB", SHARED_EMAIL, PASSWORD_B);
        Notification notifA = createNotificationIn(tenantA, userA, "STUDENT_ABSENT", "Hijo ausente hoy");
        return new Fixture(tenantA, tenantB, userA, userB, notifA);
    }

    private Tenant createTenant(String slug) {
        return tx().execute(s -> {
            Tenant t = new Tenant();
            t.setPublicUuid(UUID.randomUUID());
            t.setSlug(slug);
            t.setName("Notif-iso " + slug);
            t.setStatus(TenantStatus.ACTIVE);
            return tenantRepository.save(t);
        });
    }

    private User createUserIn(Tenant t, String username, String email, String rawPwd) {
        return tx().execute(s -> {
            User u = new User();
            u.setPublicUuid(UUID.randomUUID());
            u.setTenantId(t.getId());
            u.setFirstName(username);
            u.setLastName("of-" + t.getSlug());
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(rawPwd));
            u.setStatus(UserStatus.ACTIVE);
            u.setRoles(new String[] { UserRole.PARENT.name() });
            return userRepository.save(u);
        });
    }

    private Notification createNotificationIn(Tenant t, User u, String template, String body) {
        return TenantContext.runAs(t.getId(), () -> tx().execute(s -> {
            Notification n = new Notification();
            n.setPublicUuid(UUID.randomUUID());
            n.setTenantId(t.getId());
            n.setRecipientUserId(u.getId());
            n.setTemplateKey(template);
            n.setCategory(Category.ANNOUNCEMENT);
            n.setChannel(Channel.IN_APP);
            n.setStatus(Status.PENDING);
            n.setPayload("{\"body\":\"" + body + "\"}");
            return notificationRepository.save(n);
        }));
    }

    // ============================================================ tests

    @Nested
    @DisplayName("Cross-tenant reads")
    class CrossTenantReads {

        @Test
        @DisplayName("GET /notifications/{A's uuid} as B → 404")
        void getByPublicUuidReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    NOTIFICATIONS_BASE + "/" + f.notifA().getPublicUuid(),
                    loginB.accessToken());

            assertThat(response.getStatusCode())
                    .as("cross-tenant GET on a notification must be 404 (anti-enumeration)")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("GET /notifications as B → A's notification NOT in payload")
        void listDoesNotLeak() throws Exception {
            Fixture f = setupTenants();
            // Seed a notification for B as well so the page isn't trivially empty.
            createNotificationIn(f.tenantB(), f.userB(), "GRADE_PUBLISHED", "Nota publicada");

            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
            ResponseEntity<String> response = doGet(
                    NOTIFICATIONS_BASE + "?page=0&size=20",
                    loginB.accessToken());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode content = body.has("data") ? body.get("data").get("content") : body.get("content");
            for (JsonNode item : content) {
                String id = item.get("publicUuid").asText();
                assertThat(id)
                        .as("tenant A notification publicUuid leaked into B's listing")
                        .isNotEqualTo(f.notifA().getPublicUuid().toString());
            }
        }
    }

    @Nested
    @DisplayName("Cross-tenant writes")
    class CrossTenantWrites {

        @Test
        @DisplayName("POST /notifications/{A's uuid}/read as B → 404, A's row still unread")
        void markReadReturns404() {
            Fixture f = setupTenants();
            AuthResponse loginB = login(f.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);

            ResponseEntity<String> response = doPost(
                    NOTIFICATIONS_BASE + "/" + f.notifA().getPublicUuid() + "/read",
                    loginB.accessToken(), "{}");

            assertThat(response.getStatusCode())
                    .as("cross-tenant mark-read must be 404 (anti-enumeration)")
                    .isEqualTo(HttpStatus.NOT_FOUND);

            // Defence in depth: A's row is still PENDING.
            Notification reloaded = TenantContext.runAs(f.tenantA().getId(),
                    () -> notificationRepository.findByPublicUuid(f.notifA().getPublicUuid()).orElseThrow());
            assertThat(reloaded.getStatus())
                    .as("tenant A notification must NOT have been mutated by tenant B")
                    .isEqualTo(Status.PENDING);
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
}
