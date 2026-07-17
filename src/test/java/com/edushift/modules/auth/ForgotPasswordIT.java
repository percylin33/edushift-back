package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.ForgotPasswordRequest;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sprint 17 / BE-17.1 — full-stack IT for {@code POST /auth/forgot-password}.
 *
 * <p>Verifies the anti-enumeration contract: the endpoint always responds
 * 200 OK regardless of whether the email or tenant exists, but only queues
 * an email when the user is found in an ACTIVE tenant with an ACTIVE status.
 */
@DisplayName("Forgot-password flow (BE-17.1)")
class ForgotPasswordIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";

	@Autowired private TestRestTemplate http;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private EmailOutboxRepository outboxRepository;
	@Autowired private NotificationTemplateRepository templateRepository;

	private Tenant tenant;
	private final String tenantSlug = "forgot-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String EMAIL = "user@forgot.test";
	private static final String RAW_PWD = "EduShift2026!";

	@BeforeEach
	void setUp() {
		tenant = new Tenant();
		tenant.setName("Forgot Test Tenant");
		tenant.setSlug(tenantSlug);
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant = tenantRepository.save(tenant);

		UUID tid = tenant.getId();

		TenantContext.runAs(tid, () -> {
			User u = new User();
			u.setPublicUuid(UUID.randomUUID());
			u.setEmail(EMAIL);
			u.setFirstName("Forgot");
			u.setLastName("Test");
			u.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
			u.setStatus(UserStatus.ACTIVE);
			u.setEmailVerified(true);
			u.setPasswordHash(passwordEncoder.encode(RAW_PWD));
			userRepository.save(u);

			// Seed PASSWORD_RESET template so the email outbox row is created.
			if (templateRepository.findByKeyAndLocale("PASSWORD_RESET", "es-PE").isEmpty()) {
				NotificationTemplate tmpl = new NotificationTemplate();
				tmpl.setTemplateKey("PASSWORD_RESET");
				tmpl.setLocale("es-PE");
				tmpl.setSubject("Restablece tu contraseña — {{tenantName}}");
				tmpl.setBodyHtml("<p>Reset link: {{resetLink}}</p>");
				tmpl.setSystem(true);
				tmpl.setVersion(1);
				templateRepository.save(tmpl);
			}
			return null;
		});
	}

	private long emailOutboxCount() {
		return outboxRepository.countPending();
	}

	@Nested
	@DisplayName("POST /auth/forgot-password — anti-enumeration")
	class AntiEnumeration {

		@Test
		@DisplayName("happy path — email outbox row created")
		void happyPath() {
			var body = new ForgotPasswordRequest(EMAIL, tenantSlug);
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/forgot-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(emailOutboxCount()).isPositive();
		}

		@Test
		@DisplayName("unknown email returns 200 and no outbox row")
		void unknownEmail() {
			var body = new ForgotPasswordRequest("nobody@forgot.test", tenantSlug);
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/forgot-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(emailOutboxCount()).isZero();
		}

		@Test
		@DisplayName("unknown tenant returns 200 and no outbox row")
		void unknownTenant() {
			var body = new ForgotPasswordRequest(EMAIL, "nonexistent-slug");
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/forgot-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(emailOutboxCount()).isZero();
		}

		@Test
		@DisplayName("inactive tenant returns 200 and no outbox row")
		void inactiveTenant() {
			Tenant inactive = new Tenant();
			inactive.setName("Inactive Tenant");
			inactive.setSlug("inactive-" + UUID.randomUUID().toString().substring(0, 8));
			inactive.setStatus(TenantStatus.SUSPENDED);
			tenantRepository.save(inactive);

			var body = new ForgotPasswordRequest(EMAIL, inactive.getSlug());
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/forgot-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(emailOutboxCount()).isZero();
		}

		@Test
		@DisplayName("locked user returns 200 and no outbox row")
		void lockedUser() {
			TenantContext.runAs(tenant.getId(), () -> {
				User locked = new User();
				locked.setPublicUuid(UUID.randomUUID());
				locked.setEmail("locked@forgot.test");
				locked.setFirstName("Locked");
				locked.setLastName("User");
				locked.setRoles(new String[]{UserRole.STUDENT.name()});
				locked.setStatus(UserStatus.LOCKED);
				locked.setEmailVerified(true);
				locked.setPasswordHash(passwordEncoder.encode(RAW_PWD));
				userRepository.save(locked);
				return null;
			});

			var body = new ForgotPasswordRequest("locked@forgot.test", tenantSlug);
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/forgot-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(emailOutboxCount()).isZero();
		}
	}
}
