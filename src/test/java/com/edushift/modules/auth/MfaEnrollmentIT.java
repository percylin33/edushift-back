package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.MfaDisableRequest;
import com.edushift.modules.auth.dto.MfaEnrollVerifyRequest;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
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

/**
 * Sprint 17 / BE-17.2 — full-stack IT for MFA enrollment flow.
 *
 * <p>Verifies the two-step enrollment (start → verify) and the disable
 * flow, including all error codes.
 */
@DisplayName("MFA enrollment flow (BE-17.2)")
class MfaEnrollmentIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";

	@Autowired private TestRestTemplate http;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ObjectMapper objectMapper;

	private Tenant tenant;
	private User user;
	private final String tenantSlug = "mfa-enroll-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String EMAIL = "user@mfa-enroll.test";
	private static final String RAW_PWD = "EduShift2026!";
	private final GoogleAuthenticator totp = new GoogleAuthenticator();

	@BeforeEach
	void setUp() {
		tenant = new Tenant();
		tenant.setName("MFA Enroll Test");
		tenant.setSlug(tenantSlug);
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant = tenantRepository.save(tenant);

		TenantContext.runAs(tenant.getId(), () -> {
			user = new User();
			user.setPublicUuid(UUID.randomUUID());
			user.setEmail(EMAIL);
			user.setFirstName("Mfa");
			user.setLastName("Enroll");
			user.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
			user.setStatus(UserStatus.ACTIVE);
			user.setEmailVerified(true);
			user.setPasswordHash(passwordEncoder.encode(RAW_PWD));
			user.setMfaEnabled(false);
			user = userRepository.save(user);
			return null;
		});
	}

	/** Login and return the access token. */
	private String login() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("X-Tenant-Slug", tenantSlug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", EMAIL, RAW_PWD);
		ResponseEntity<String> resp = http.exchange(
				AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode json = objectMapper.readTree(resp.getBody());
		return json.get("accessToken").asText();
	}

	/** Start MFA enrollment and return the secret. */
	private String startEnrollment(String token) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<String> resp = http.exchange(
				AUTH_BASE + "/mfa/enroll/start", HttpMethod.POST,
				new HttpEntity<>(headers), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode json = objectMapper.readTree(resp.getBody());
		return json.get("data").get("secret").asText();
	}

	/** Generate a valid TOTP code for a given secret at the current time. */
	private int currentTotpCode(String secret) {
		return totp.getTotpPassword(secret);
	}

	@Nested
	@DisplayName("POST /mfa/enroll/start")
	class Start {

		@Test
		@DisplayName("returns secret + QR + otpauth URI")
		void happyPath() throws Exception {
			String token = login();
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(token);

			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/enroll/start", HttpMethod.POST,
					new HttpEntity<>(headers), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data.get("secret").asText()).isNotBlank();
			assertThat(data.get("qrCodeDataUrl").asText()).isNotBlank();
			assertThat(data.get("otpauthUri").asText()).startsWith("otpauth://");
		}

		@Test
		@DisplayName("returns 401 without auth")
		void unauthenticated() {
			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/enroll/start", HttpMethod.POST,
					new HttpEntity<>(new HttpHeaders()), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		@Test
		@DisplayName("returns 400 when MFA already enabled")
		void alreadyEnabled() throws Exception {
			String token = login();
			String secret = startEnrollment(token);
			int code = currentTotpCode(secret);
			MfaEnrollVerifyRequest body = new MfaEnrollVerifyRequest(secret, String.valueOf(code));
			HttpHeaders h = new HttpHeaders();
			h.setBearerAuth(token);
			h.setContentType(MediaType.APPLICATION_JSON);
			http.exchange(AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
					new HttpEntity<>(body, h), String.class);

			// Try starting enrollment again.
			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/enroll/start", HttpMethod.POST,
					new HttpEntity<>(new HttpHeaders() {{ setBearerAuth(token); }}), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(resp.getBody()).contains("MFA_ALREADY_ENABLED");
		}
	}

	@Nested
	@DisplayName("POST /mfa/enroll/verify")
	class Verify {

		@Test
		@DisplayName("returns recovery codes and enables MFA")
		void happyPath() throws Exception {
			String token = login();
			String secret = startEnrollment(token);
			int code = currentTotpCode(secret);

			HttpHeaders h = new HttpHeaders();
			h.setBearerAuth(token);
			h.setContentType(MediaType.APPLICATION_JSON);
			MfaEnrollVerifyRequest body = new MfaEnrollVerifyRequest(secret, String.valueOf(code));

			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
					new HttpEntity<>(body, h), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data.get("recoveryCodes").isArray()).isTrue();
			assertThat(data.get("recoveryCodes")).hasSize(10);

			// Verify MFA is now enabled in DB.
			TenantContext.runAs(tenant.getId(), () -> {
				User reloaded = userRepository.findById(user.getId()).orElseThrow();
				assertThat(reloaded.isMfaEnabled()).isTrue();
				assertThat(reloaded.getMfaSecretHash()).isNotBlank();
				assertThat(reloaded.getMfaRecoveryCodesHash()).hasSize(10);
				return null;
			});
		}

		@Test
		@DisplayName("invalid TOTP code returns 401")
		void invalidCode() throws Exception {
			String token = login();
			String secret = startEnrollment(token);

			HttpHeaders h = new HttpHeaders();
			h.setBearerAuth(token);
			h.setContentType(MediaType.APPLICATION_JSON);
			MfaEnrollVerifyRequest body = new MfaEnrollVerifyRequest(secret, "000000");

			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
					new HttpEntity<>(body, h), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(resp.getBody()).contains("INVALID_TOTP_CODE");
		}
	}

	@Nested
	@DisplayName("POST /mfa/disable")
	class Disable {

		@Test
		@DisplayName("disables MFA with password + valid TOTP code")
		void happyPath() throws Exception {
			String token = login();
			String secret = startEnrollment(token);
			int code = currentTotpCode(secret);

			// Complete enrollment.
			HttpHeaders h = new HttpHeaders();
			h.setBearerAuth(token);
			h.setContentType(MediaType.APPLICATION_JSON);
			http.exchange(AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
					new HttpEntity<>(new MfaEnrollVerifyRequest(secret, String.valueOf(code)), h), String.class);

			// Disable.
			HttpHeaders disableHeaders = new HttpHeaders();
			disableHeaders.setBearerAuth(token);
			disableHeaders.setContentType(MediaType.APPLICATION_JSON);
			int newCode = currentTotpCode(secret);
			MfaDisableRequest disableBody = new MfaDisableRequest(RAW_PWD, String.valueOf(newCode));

			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/disable", HttpMethod.POST,
					new HttpEntity<>(disableBody, disableHeaders), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

			// Verify MFA is disabled in DB.
			TenantContext.runAs(tenant.getId(), () -> {
				User reloaded = userRepository.findById(user.getId()).orElseThrow();
				assertThat(reloaded.isMfaEnabled()).isFalse();
				assertThat(reloaded.getMfaSecretHash()).isNull();
				return null;
			});
		}

		@Test
		@DisplayName("wrong password returns 401")
		void wrongPassword() throws Exception {
			String token = login();
			String secret = startEnrollment(token);
			int code = currentTotpCode(secret);

			HttpHeaders h = new HttpHeaders();
			h.setBearerAuth(token);
			h.setContentType(MediaType.APPLICATION_JSON);
			http.exchange(AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
					new HttpEntity<>(new MfaEnrollVerifyRequest(secret, String.valueOf(code)), h), String.class);

			int newCode = currentTotpCode(secret);
			MfaDisableRequest disableBody = new MfaDisableRequest("WrongPassword-1!", String.valueOf(newCode));
			HttpHeaders dh = new HttpHeaders();
			dh.setBearerAuth(token);
			dh.setContentType(MediaType.APPLICATION_JSON);

			ResponseEntity<String> resp = http.exchange(
					AUTH_BASE + "/mfa/disable", HttpMethod.POST,
					new HttpEntity<>(disableBody, dh), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(resp.getBody()).contains("INVALID_PASSWORD");
		}
	}
}
