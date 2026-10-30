package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.entity.RefreshToken;
import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.events.UserStatusChangeEvent;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.users.dto.UserDetailResponse;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * Sprint 14 (MVP Closure) / DEBT-AUTH-4 — full-stack IT.
 *
 * <p>End-to-end verification that:
 * <ul>
 *   <li>when an admin disables a user, all their active refresh tokens
 *       get revoked with reason {@code ADMIN_REVOKE}.</li>
 *   <li>the next {@code /auth/refresh} with one of those tokens returns
 *       401.</li>
 * </ul>
 */
class AuthStatusChangeIT extends IntegrationTest {

	@LocalServerPort private int port;

	@Autowired private TestRestTemplate http;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RefreshTokenRepository refreshTokenRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ApplicationEventPublisher eventPublisher;
	@Autowired private PlatformTransactionManager txManager;

	private Tenant tenant;
	private User student;
	private final String tenantSlug = "status-" + UUID.randomUUID().toString().substring(0, 8);

	@BeforeEach
	void setUp() {
		tenant = newTestTenant();
		tenant.setName("Status Test Tenant");
		tenant.setSlug(tenantSlug);
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant = tenantRepository.saveAndFlush(tenant);
		com.edushift.shared.multitenancy.TenantContext.set(tenant.getId());

		student = new User();
		student.setPublicUuid(UUID.randomUUID());
		student.setEmail("victim@status.test");
		student.setFirstName("Victim");
		student.setLastName("Status");
		student.setRoles(new String[]{UserRole.STUDENT.name()});
		student.setStatus(UserStatus.ACTIVE);
		student.setEmailVerified(true);
		student.setPasswordHash(passwordEncoder.encode("EduShift2026!"));
		student = com.edushift.shared.multitenancy.TenantContext.runAs(
				tenant.getId(), () -> userRepository.saveAndFlush(student));
	}

	@org.junit.jupiter.api.AfterEach
	void clearTenant() {
		com.edushift.shared.multitenancy.TenantContext.clear();
	}

	private String login() {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		h.set("X-Tenant-Slug", tenantSlug);
		String body = "{\"email\":\"victim@status.test\",\"password\":\"EduShift2026!\"}";
		ResponseEntity<java.util.Map> r = http.exchange(
				"/v1/auth/login",
				HttpMethod.POST,
				new HttpEntity<>(body, h),
				java.util.Map.class);
		assertThat(r.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
		java.util.Map body2 = r.getBody();
		assertThat(body2).isNotNull();
		assertThat(body2).doesNotContainKeys("accessToken", "refreshToken");
		return sessionCookie(r, "edushift_refresh_token");
	}

	private void persistFakeRefreshTokenForStudent(String rawToken) {
		// Mirror the AuthServiceImpl.persistRefreshToken helper: insert a row with
		// SHA-256 hex(token) so revokeAllByUser(userId, ADMIN_REVOKE) can match it.
		RefreshToken rt = new RefreshToken();
		rt.setTokenHash(sha256Hex(rawToken));
		rt.setUserId(student.getId());
		rt.setExpiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7));
		rt.setParentTokenId(null);
		refreshTokenRepository.saveAndFlush(rt);
	}

	private ResponseEntity<String> refresh(String refreshToken) {
		HttpHeaders h = new HttpHeaders();
		h.add(HttpHeaders.COOKIE, "edushift_refresh_token=" + refreshToken);
		h.setContentType(MediaType.APPLICATION_JSON);
		h.set("X-Tenant-Slug", tenantSlug);
		String body = "{\"refreshToken\":\"" + refreshToken + "\"}";
		return http.exchange(
				"/v1/auth/refresh",
				HttpMethod.POST,
				new HttpEntity<>(body, h),
				String.class);
	}

	@Test
	@DisplayName("DEBT-AUTH-4: admin disables user → refresh tokens revoked with ADMIN_REVOKE")
	void disablingUserRevokesActiveRefreshTokens() {
		// Login (which persists a refresh row in the DB).
		String refreshToken = login();
		// sanity: there should be 1 active row.
		assertThat(refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken)))
				.isPresent();

		// Publish the same status-transition event as the users module,
		// inside the transaction that persists the change. This exercises
		// the real AFTER_COMMIT token-revocation listener.
		com.edushift.shared.multitenancy.TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).executeWithoutResult(status -> {
					User managed = userRepository.findByPublicUuid(student.getPublicUuid())
							.orElseThrow();
					UserStatus oldStatus = managed.getStatus();
					managed.setStatus(UserStatus.SUSPENDED);
					userRepository.saveAndFlush(managed);
					eventPublisher.publishEvent(new UserStatusChangeEvent(
							managed.getPublicUuid(), oldStatus, UserStatus.SUSPENDED,
							"it-disable", null));
			});
			return null;
		});

		// The user-status event listener revokes sessions after the status
		// update commits; assert its persisted effect directly.
		var stored = refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken));
		assertThat(stored).isPresent();
		assertThat(stored.get().isRevoked()).isTrue();
		assertThat(stored.get().getRevokedReason()).isEqualTo(RevocationReason.ADMIN_REVOKE);

		// /refresh returns 401.
		ResponseEntity<String> r = refresh(refreshToken);
		assertThat(r.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
	}

	private static String sha256Hex(String value) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
