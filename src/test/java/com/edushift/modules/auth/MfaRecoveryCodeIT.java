package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Sprint 17 / BE-17.2 — full-stack IT for MFA recovery codes.
 *
 * <p>Verifies that recovery codes are returned during enrollment, can be
 * used to complete MFA challenge, and are consumed on first use.
 */
@DisplayName("MFA recovery codes (BE-17.2)")
class MfaRecoveryCodeIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";

	@Autowired private TestRestTemplate http;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ObjectMapper objectMapper;

	private Tenant tenant;
	private final String tenantSlug = "mfa-rc-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String EMAIL = "user@mfa-rc.test";
	private static final String RAW_PWD = "EduShift2026!";
	private final GoogleAuthenticator totp = new GoogleAuthenticator();

	private String accessToken;
	private List<String> recoveryCodes;

	@BeforeEach
	void setUp() throws Exception {
		tenant = new Tenant();
		tenant.setName("MFA Recovery Code Test");
		tenant.setSlug(tenantSlug);
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant = tenantRepository.save(tenant);

		TenantContext.runAs(tenant.getId(), () -> {
			User u = new User();
			u.setPublicUuid(UUID.randomUUID());
			u.setEmail(EMAIL);
			u.setFirstName("Mfa");
			u.setLastName("RC");
			u.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
			u.setStatus(UserStatus.ACTIVE);
			u.setEmailVerified(true);
			u.setPasswordHash(passwordEncoder.encode(RAW_PWD));
			u.setMfaEnabled(false);
			userRepository.save(u);
			return null;
		});

		// Login + enroll MFA, capturing the recovery codes.
		accessToken = loginGetToken();
		HttpHeaders h = new HttpHeaders();
		h.setBearerAuth(accessToken);
		h.setContentType(MediaType.APPLICATION_JSON);

		// Start enrollment.
		ResponseEntity<String> startResp = http.exchange(
				AUTH_BASE + "/mfa/enroll/start", HttpMethod.POST,
				new HttpEntity<>(h), String.class);
		String secret = objectMapper.readTree(startResp.getBody())
				.get("data").get("secret").asText();
		int code = totp.getTotpPassword(secret);

		// Complete enrollment — capture recovery codes.
		ResponseEntity<String> verifyResp = http.exchange(
				AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
				new HttpEntity<>(new MfaEnrollVerifyRequest(secret, String.valueOf(code)), h),
				String.class);
		JsonNode data = objectMapper.readTree(verifyResp.getBody()).get("data");
		recoveryCodes = objectMapper.readValue(
				data.get("recoveryCodes").traverse(), List.class);
	}

	private String loginGetToken() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("X-Tenant-Slug", tenantSlug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", EMAIL, RAW_PWD);
		ResponseEntity<String> resp = http.exchange(
				AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		return objectMapper.readTree(resp.getBody()).get("accessToken").asText();
	}

	@Test
	@DisplayName("recovery code completes MFA challenge successfully")
	void recoveryCodeCompletesChallenge() throws Exception {
		// Login → get mfaToken.
		HttpHeaders loginHeaders = new HttpHeaders();
		loginHeaders.setContentType(MediaType.APPLICATION_JSON);
		loginHeaders.add("X-Tenant-Slug", tenantSlug);
		String loginBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", EMAIL, RAW_PWD);
		ResponseEntity<String> loginResp = http.exchange(
				AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(loginBody, loginHeaders), String.class);
		String mfaToken = objectMapper.readTree(loginResp.getBody()).get("mfaToken").asText();

		// Challenge with first recovery code.
		HttpHeaders challengeHeaders = new HttpHeaders();
		challengeHeaders.setContentType(MediaType.APPLICATION_JSON);
		challengeHeaders.setBearerAuth(mfaToken);
		challengeHeaders.add("X-Tenant-Slug", tenantSlug);
		String challengeBody = String.format("{\"code\":\"%s\"}", recoveryCodes.get(0));

		ResponseEntity<String> resp = http.exchange(
				AUTH_BASE + "/mfa/challenge", HttpMethod.POST,
				new HttpEntity<>(challengeBody, challengeHeaders), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
		assertThat(data.get("accessToken").asText()).isNotBlank();
		assertThat(data.get("user").get("email").asText()).isEqualTo(EMAIL);
	}

	@Test
	@DisplayName("same recovery code cannot be used twice")
	void recoveryCodeConsumedOnFirstUse() throws Exception {
		// Login → get mfaToken.
		HttpHeaders loginHeaders = new HttpHeaders();
		loginHeaders.setContentType(MediaType.APPLICATION_JSON);
		loginHeaders.add("X-Tenant-Slug", tenantSlug);
		String loginBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", EMAIL, RAW_PWD);

		// First use — should succeed.
		String mfaToken1 = objectMapper.readTree(
				http.exchange(AUTH_BASE + "/login", HttpMethod.POST,
						new HttpEntity<>(loginBody, loginHeaders), String.class).getBody())
				.get("mfaToken").asText();
		HttpHeaders ch1 = new HttpHeaders();
		ch1.setContentType(MediaType.APPLICATION_JSON);
		ch1.setBearerAuth(mfaToken1);
		ch1.add("X-Tenant-Slug", tenantSlug);
		http.exchange(AUTH_BASE + "/mfa/challenge", HttpMethod.POST,
				new HttpEntity<>(String.format("{\"code\":\"%s\"}", recoveryCodes.get(1)), ch1),
				String.class);

		// Second use (same recovery code) — should fail.
		String mfaToken2 = objectMapper.readTree(
				http.exchange(AUTH_BASE + "/login", HttpMethod.POST,
						new HttpEntity<>(loginBody, loginHeaders), String.class).getBody())
				.get("mfaToken").asText();
		HttpHeaders ch2 = new HttpHeaders();
		ch2.setContentType(MediaType.APPLICATION_JSON);
		ch2.setBearerAuth(mfaToken2);
		ch2.add("X-Tenant-Slug", tenantSlug);
		ResponseEntity<String> resp = http.exchange(
				AUTH_BASE + "/mfa/challenge", HttpMethod.POST,
				new HttpEntity<>(String.format("{\"code\":\"%s\"}", recoveryCodes.get(1)), ch2),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(resp.getBody()).contains("INVALID_MFA_CODE");
	}
}
