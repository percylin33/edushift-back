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
 * Sprint 17 / BE-17.2 — cross-tenant isolation IT for MFA.
 *
 * <p>Proves that MFA tokens and sessions are correctly scoped to their tenant.
 * An MFA token issued in tenant A cannot be used to complete the MFA
 * challenge in tenant B (the {@code X-Tenant-Slug} header is cross-checked
 * against the JWT's tenant_id claim).
 */
@DisplayName("MFA cross-tenant isolation (BE-17.2)")
class MfaTenantIsolationIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";

	@Autowired private TestRestTemplate http;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ObjectMapper objectMapper;

	private Tenant tenantA;
	private Tenant tenantB;
	private final String slugA = "mfa-iso-a-" + UUID.randomUUID().toString().substring(0, 8);
	private final String slugB = "mfa-iso-b-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String EMAIL_A = "user-a@mfa-iso.test";
	private static final String EMAIL_B = "user-b@mfa-iso.test";
	private static final String RAW_PWD = "EduShift2026!";
	private final GoogleAuthenticator totp = new GoogleAuthenticator();

	@BeforeEach
	void setUp() throws Exception {
		tenantA = createTenant("MFA Isolation A", slugA);
		tenantB = createTenant("MFA Isolation B", slugB);

		createUserAndEnableMfa(tenantA, EMAIL_A);
		createUserAndEnableMfa(tenantB, EMAIL_B);
	}

	private Tenant createTenant(String name, String slug) {
		Tenant t = new Tenant();
		t.setName(name);
		t.setSlug(slug);
		t.setStatus(TenantStatus.ACTIVE);
		return tenantRepository.save(t);
	}

	/** Create a user, login, enroll MFA, get the TOTP secret back. */
	private String createUserAndEnableMfa(Tenant t, String email) throws Exception {
		TenantContext.runAs(t.getId(), () -> {
			User u = new User();
			u.setPublicUuid(UUID.randomUUID());
			u.setEmail(email);
			u.setFirstName("User");
			u.setLastName(t.getSlug());
			u.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
			u.setStatus(UserStatus.ACTIVE);
			u.setEmailVerified(true);
			u.setPasswordHash(passwordEncoder.encode(RAW_PWD));
			u.setMfaEnabled(false);
			userRepository.save(u);
			return null;
		});

		// Login.
		HttpHeaders loginHeaders = new HttpHeaders();
		loginHeaders.setContentType(MediaType.APPLICATION_JSON);
		loginHeaders.add("X-Tenant-Slug", t.getSlug());
		String loginBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, RAW_PWD);
		String loginJson = http.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(loginBody, loginHeaders), String.class).getBody();
		String token = objectMapper.readTree(loginJson).get("accessToken").asText();

		// Enroll MFA.
		HttpHeaders enrollHeaders = new HttpHeaders();
		enrollHeaders.setBearerAuth(token);
		enrollHeaders.setContentType(MediaType.APPLICATION_JSON);

		String startJson = http.exchange(AUTH_BASE + "/mfa/enroll/start", HttpMethod.POST,
				new HttpEntity<>(enrollHeaders), String.class).getBody();
		String secret = objectMapper.readTree(startJson).get("data").get("secret").asText();
		int code = totp.getTotpPassword(secret);

		http.exchange(AUTH_BASE + "/mfa/enroll/verify", HttpMethod.POST,
				new HttpEntity<>(new MfaEnrollVerifyRequest(secret, String.valueOf(code)),
						enrollHeaders), String.class);
		return secret;
	}

	@Test
	@DisplayName("mfaToken from tenant A cannot be used in tenant B")
	void mfaTokenCrossTenant() throws Exception {
		// Login as user A → get mfaToken.
		HttpHeaders loginAHeaders = new HttpHeaders();
		loginAHeaders.setContentType(MediaType.APPLICATION_JSON);
		loginAHeaders.add("X-Tenant-Slug", slugA);
		String loginBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", EMAIL_A, RAW_PWD);
		ResponseEntity<String> loginResp = http.exchange(
				AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(loginBody, loginAHeaders), String.class);
		String mfaToken = objectMapper.readTree(loginResp.getBody()).get("mfaToken").asText();

		// Get the TOTP secret for user A to generate a valid code.
		String secretA = TenantContext.runAs(tenantA.getId(), () ->
				userRepository.findByEmail(EMAIL_A).orElseThrow().getMfaSecretHash()
		);
		int code = totp.getTotpPassword(secretA);

		// Try to use mfaToken (issued for tenant A) with X-Tenant-Slug = tenant B.
		HttpHeaders challengeHeaders = new HttpHeaders();
		challengeHeaders.setContentType(MediaType.APPLICATION_JSON);
		challengeHeaders.setBearerAuth(mfaToken);
		challengeHeaders.add("X-Tenant-Slug", slugB); // WRONG tenant
		String challengeBody = String.format("{\"code\":\"%06d\"}", code);

		ResponseEntity<String> resp = http.exchange(
				AUTH_BASE + "/mfa/challenge", HttpMethod.POST,
				new HttpEntity<>(challengeBody, challengeHeaders), String.class);

		// The JWT's tenant_id claim won't match slug B → MFA_TOKEN_INVALID.
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(resp.getBody()).contains("MFA_TOKEN_INVALID");
	}
}
