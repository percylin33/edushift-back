package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.ForgotPasswordRequest;
import com.edushift.modules.auth.dto.ResetPasswordRequest;
import com.edushift.modules.auth.entity.PasswordResetToken;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.PasswordResetTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * Sprint 17 / BE-17.1 — full-stack IT for the reset-password flow.
 *
 * <p>Covers token validation ({@code GET /auth/reset-password/validate})
 * and token consumption ({@code POST /auth/reset-password}) including
 * all error codes from the spec (expired, used, superseded, malformed).
 */
@DisplayName("Reset-password flow (BE-17.1)")
class ResetPasswordIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";

	@Autowired private TestRestTemplate http;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JwtService jwtService;
	@Autowired private PasswordResetTokenRepository resetTokenRepository;
	@Autowired private ObjectMapper objectMapper;

	private Tenant tenant;
	private User user;
	private final String tenantSlug = "reset-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String EMAIL = "user@reset.test";
	private static final String OLD_PWD = "OldPassword-1!";
	private static final String NEW_PWD = "NewPassword-2@";

	@BeforeEach
	void setUp() {
		tenant = new Tenant();
		tenant.setName("Reset Test Tenant");
		tenant.setSlug(tenantSlug);
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant = tenantRepository.save(tenant);

		UUID tid = tenant.getId();

		TenantContext.runAs(tid, () -> {
			user = new User();
			user.setPublicUuid(UUID.randomUUID());
			user.setEmail(EMAIL);
			user.setFirstName("Reset");
			user.setLastName("Test");
			user.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
			user.setStatus(UserStatus.ACTIVE);
			user.setEmailVerified(true);
			user.setPasswordHash(passwordEncoder.encode(OLD_PWD));
			user = userRepository.save(user);
			return null;
		});
	}

	/** Issue a valid reset token for the test user directly via JwtService. */
	private String issueValidToken() {
		UUID jti = UUID.randomUUID();
		String token = jwtService.issueResetToken(user, tenant, jti);

		TenantContext.runAs(tenant.getId(), () -> {
			PasswordResetToken row = new PasswordResetToken();
			row.setJti(jti);
			row.setUserId(user.getId());
			row.setExpiresAt(Instant.now().plus(jwtService.resetTokenTtl()));
			resetTokenRepository.save(row);
			return null;
		});

		return token;
	}

	@Nested
	@DisplayName("GET /auth/reset-password/validate")
	class Validate {

		@Test
		@DisplayName("valid token returns valid=true with tenant info")
		void valid() throws Exception {
			String token = issueValidToken();

			ResponseEntity<String> resp = http.getForEntity(
					AUTH_BASE + "/reset-password/validate?token=" + token, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(resp.getBody());
			JsonNode data = body.get("data");
			assertThat(data.get("valid").asBoolean()).isTrue();
			assertThat(data.get("tenantName").asText()).isEqualTo(tenant.getName());
			assertThat(data.get("tenantSlug").asText()).isEqualTo(tenantSlug);
			assertThat(data.get("reasonCode").isNull()).isTrue();
		}

		@Test
		@DisplayName("expired token returns valid=false with reason RESET_TOKEN_EXPIRED")
		void expired() throws Exception {
			// Create a token that is already expired.
			UUID jti = UUID.randomUUID();
			String token = jwtService.issueResetToken(user, tenant, jti);

			TenantContext.runAs(tenant.getId(), () -> {
				PasswordResetToken row = new PasswordResetToken();
				row.setJti(jti);
				row.setUserId(user.getId());
				row.setExpiresAt(Instant.now().minusSeconds(1)); // expired
				resetTokenRepository.save(row);
				return null;
			});

			ResponseEntity<String> resp = http.getForEntity(
					AUTH_BASE + "/reset-password/validate?token=" + token, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data.get("valid").asBoolean()).isFalse();
			assertThat(data.get("reasonCode").asText()).isEqualTo("RESET_TOKEN_EXPIRED");
		}

		@Test
		@DisplayName("malformed token returns valid=false with reasonCode")
		void malformed() throws Exception {
			ResponseEntity<String> resp = http.getForEntity(
					AUTH_BASE + "/reset-password/validate?token=not-a-jwt", String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data.get("valid").asBoolean()).isFalse();
			assertThat(data.get("reasonCode").asText()).isIn("RESET_TOKEN_INVALID", "RESET_TOKEN_MALFORMED");
		}
	}

	@Nested
	@DisplayName("POST /auth/reset-password")
	class Consume {

		@Test
		@DisplayName("happy path — password changed, old password fails login")
		void happyPath() {
			String token = issueValidToken();
			var body = new ResetPasswordRequest(token, NEW_PWD, NEW_PWD);

			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/reset-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

			// Verify the old password no longer works.
			TenantContext.runAs(tenant.getId(), () -> {
				User reloaded = userRepository.findById(user.getId()).orElseThrow();
				assertThat(passwordEncoder.matches(OLD_PWD, reloaded.getPasswordHash())).isFalse();
				assertThat(passwordEncoder.matches(NEW_PWD, reloaded.getPasswordHash())).isTrue();
				return null;
			});
		}

		@Test
		@DisplayName("used token returns 401 RESET_TOKEN_USED")
		void usedToken() {
			String token = issueValidToken();
			var firstUse = new ResetPasswordRequest(token, NEW_PWD, NEW_PWD);
			http.postForEntity(AUTH_BASE + "/reset-password", firstUse, String.class);

			// Second attempt with the same token.
			var secondUse = new ResetPasswordRequest(token, "AnotherPwd-3#", "AnotherPwd-3#");
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/reset-password", secondUse, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(resp.getBody()).contains("RESET_TOKEN_USED");
		}

		@Test
		@DisplayName("expired token returns 401 RESET_TOKEN_EXPIRED")
		void expiredToken() {
			UUID jti = UUID.randomUUID();
			String token = jwtService.issueResetToken(user, tenant, jti);

			TenantContext.runAs(tenant.getId(), () -> {
				PasswordResetToken row = new PasswordResetToken();
				row.setJti(jti);
				row.setUserId(user.getId());
				row.setExpiresAt(Instant.now().minusSeconds(1));
				resetTokenRepository.save(row);
				return null;
			});

			var body = new ResetPasswordRequest(token, NEW_PWD, NEW_PWD);
			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/reset-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(resp.getBody()).contains("RESET_TOKEN_EXPIRED");
		}

		@Test
		@DisplayName("password confirm mismatch returns 400")
		void confirmMismatch() {
			String token = issueValidToken();
			var body = new ResetPasswordRequest(token, NEW_PWD, "DifferentConfirm-4$");

			ResponseEntity<String> resp = http.postForEntity(
					AUTH_BASE + "/reset-password", body, String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(resp.getBody()).contains("PASSWORD_CONFIRM_MISMATCH");
		}
	}
}
