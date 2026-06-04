package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
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
 * End-to-end integration test that proves the multi-tenancy isolation
 * promised by Sprint 1's auth module.
 *
 * <h3>What is tested</h3>
 * Two tenants ({@code A} and {@code B}) are created with one user each. The
 * users intentionally share the same email so that any bug in the
 * tenant-scoped lookups (Hibernate {@code @TenantId}, JWT
 * {@code tenant_id} claim, {@code refresh_tokens.tenant_id}) would surface
 * as a cross-tenant data leak — exactly the failure mode this test is here
 * to prevent.
 *
 * <h3>Scenarios</h3>
 * <ol>
 *   <li>Login isolation — same email, different tenants, different passwords.</li>
 *   <li>Bearer routing — {@code /me} returns the user owned by the JWT's
 *       {@code tenant_id}, not the user with that email in another tenant.</li>
 *   <li>Refresh / logout isolation — revoking tenant A's chain does not
 *       touch tenant B's tokens.</li>
 *   <li>Tenant lifecycle — non-{@code ACTIVE} tenants reject login;
 *       unknown slugs return 404.</li>
 * </ol>
 *
 * <h3>Test stack</h3>
 * Inherits {@link IntegrationTest} which provides a Postgres 16 container
 * (Testcontainers), the {@code test} Spring profile (Redis disabled, JWT
 * secret pinned) and a randomly-ported embedded Tomcat. Each test creates
 * its own tenant pair with UUID-suffixed slugs so methods don't interfere.
 */
@DisplayName("Auth multi-tenancy isolation")
class AuthTenantIsolationIT extends IntegrationTest {

	/**
	 * Path relative to the servlet context-path. {@code TestRestTemplate}
	 * auto-prepends {@code server.servlet.context-path=/api} to its root URI
	 * (via Spring Boot's {@code TestRestTemplateContextCustomizer}), so callers
	 * must NOT include {@code /api} here — doing so yields {@code /api/api/...}
	 * which Spring Security rejects with 401 because it doesn't match the
	 * public allow-list. Surfaced when an earlier draft of this IT used
	 * {@code "/api/v1/auth"} and every login call returned
	 * {@code 401 UNAUTHORIZED} (see surefire-reports for the run before the fix).
	 */
	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "shared@isolation.test";

	private static final String PASSWORD_A = "PasswordOfTenantA-1!";

	private static final String PASSWORD_B = "PasswordOfTenantB-2!";

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PlatformTransactionManager txManager;

	@Autowired
	private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) {
			tx = new TransactionTemplate(txManager);
		}
		return tx;
	}

	// ===========================================================================
	// Login isolation — the most important guarantee of the multi-tenant model
	// ===========================================================================

	@Nested
	@DisplayName("Login isolation across tenants")
	class LoginIsolation {

		@Test
		@DisplayName("login(A, passwordA) succeeds with a JWT carrying tenant A's id")
		void loginToOwnTenantSucceeds() throws Exception {
			TenantPair pair = seedTenantPair();

			ResponseEntity<String> response = doLogin(pair.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("accessToken").asText()).isNotBlank();
			assertThat(body.get("refreshToken").asText()).isNotBlank();
			assertThat(body.get("expiresInSec").asLong()).isPositive();
			assertThat(body.get("user").get("email").asText()).isEqualTo(SHARED_EMAIL);

			// The JWT itself must claim tenant A — the fundamental security
			// invariant. We decode the payload without verifying the signature
			// because we only care about the routing claim here; signature
			// validation is exercised by JwtServiceImplTest.
			JsonNode claims = decodeJwtPayload(body.get("accessToken").asText());
			assertThat(claims.get("tenant_id").asText())
					.as("JWT tenant_id claim must equal tenant A's internal id")
					.isEqualTo(pair.tenantA().getId().toString());
			assertThat(claims.get("tenant_slug").asText())
					.isEqualTo(pair.tenantA().getSlug());
		}

		@Test
		@DisplayName("login(A, passwordB) returns 401 — B's password must be invisible to tenant A")
		void crossTenantPasswordIsRejected() throws Exception {
			TenantPair pair = seedTenantPair();

			// passwordB belongs to user-B inside tenant B. Even though both users
			// share the email, the user lookup MUST be scoped to tenant A first;
			// finding nothing, the bcrypt compare never runs and we get 401.
			ResponseEntity<String> response = doLogin(pair.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_B);

			assertUnauthorizedWithCode(response, "BAD_CREDENTIALS");
		}

		@Test
		@DisplayName("login(A, wrong-password) returns 401 — sanity baseline")
		void wrongPasswordReturns401() throws Exception {
			TenantPair pair = seedTenantPair();

			ResponseEntity<String> response = doLogin(pair.tenantA().getSlug(),
					SHARED_EMAIL, "definitely-not-the-real-password");

			assertUnauthorizedWithCode(response, "BAD_CREDENTIALS");
		}

	}

	// ===========================================================================
	// Bearer-driven endpoints — the JWT carries the tenant; /me uses it
	// ===========================================================================

	@Nested
	@DisplayName("Bearer-driven /me")
	class BearerDriven {

		@Test
		@DisplayName("/me with bearerA returns user-A — never user-B (same email, different tenant)")
		void meReturnsBearerOwnerNotSameEmailUserOfOtherTenant() throws Exception {
			TenantPair pair = seedTenantPair();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> meResponse = doGetWithBearer("/me", loginA.accessToken());

			assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(meResponse.getBody());
			JsonNode user = body.get("data");
			assertThat(user.get("email").asText()).isEqualTo(SHARED_EMAIL);
			// The decisive assertion — the publicUuid must match user-A and NOT
			// user-B, which is the whole point of tenant scoping with a shared
			// email.
			assertThat(user.get("publicUuid").asText())
					.as("/me must return user-A's publicUuid (tenant A's bearer)")
					.isEqualTo(pair.userA().getPublicUuid().toString())
					.isNotEqualTo(pair.userB().getPublicUuid().toString());
		}

		@Test
		@DisplayName("/me with no bearer returns 401 (security chain rejects via entry point)")
		void meWithoutBearerReturns401() {
			ResponseEntity<String> response = doGet("/me");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		@Test
		@DisplayName("/me with the refresh token in the bearer header returns 401")
		void meWithRefreshTokenReturns401() throws Exception {
			TenantPair pair = seedTenantPair();
			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			// JwtAuthenticationFilter must reject typ=refresh tokens — otherwise
			// a leaked refresh would silently grant API access.
			ResponseEntity<String> response = doGetWithBearer("/me", loginA.refreshToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

	}

	// ===========================================================================
	// Refresh & logout isolation — operations on tenant A do not affect tenant B
	// ===========================================================================

	@Nested
	@DisplayName("Refresh / logout isolation")
	class RefreshLogoutIsolation {

		@Test
		@DisplayName("logout(refreshA) does not invalidate refreshB — chains are tenant-scoped")
		void logoutOfOneTenantLeavesOtherTenantsRefreshUntouched() throws Exception {
			TenantPair pair = seedTenantPair();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			AuthResponse loginB = login(pair.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);

			// Revoke A's refresh.
			ResponseEntity<String> logoutA = doLogout(loginA.refreshToken());
			assertThat(logoutA.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

			// A's refresh is now revoked → /refresh returns 401 TOKEN_REUSED.
			ResponseEntity<String> refreshANow = doRefresh(loginA.refreshToken());
			assertUnauthorizedWithCode(refreshANow, "TOKEN_REUSED");

			// B's refresh is untouched → /refresh rotates successfully.
			ResponseEntity<String> refreshBNow = doRefresh(loginB.refreshToken());
			assertThat(refreshBNow.getStatusCode())
					.as("logout of tenant A must not affect tenant B's refresh chain")
					.isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(refreshBNow.getBody());
			assertThat(body.get("accessToken").asText()).isNotBlank();
			assertThat(body.get("refreshToken").asText())
					.as("rotation produces a new refresh distinct from the input")
					.isNotEqualTo(loginB.refreshToken());
		}

	}

	// ===========================================================================
	// Tenant lifecycle gating — non-ACTIVE / non-existent tenants
	// ===========================================================================

	@Nested
	@DisplayName("Tenant lifecycle gating")
	class TenantLifecycleGating {

		@Test
		@DisplayName("login on a SUSPENDED tenant returns 401 TENANT_INACTIVE")
		void suspendedTenantCannotLogin() throws Exception {
			Tenant suspended = createTenant("it-suspended-", TenantStatus.SUSPENDED);
			createUser(suspended, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE);

			ResponseEntity<String> response = doLogin(suspended.getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			assertUnauthorizedWithCode(response, "TENANT_INACTIVE");
		}

		@Test
		@DisplayName("login on an unknown tenant slug returns 404 RESOURCE_NOT_FOUND")
		void unknownTenantReturns404() throws Exception {
			ResponseEntity<String> response = doLogin(
					"it-ghost-" + UUID.randomUUID().toString().substring(0, 8),
					SHARED_EMAIL, PASSWORD_A);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("errors").get(0).get("code").asText())
					.isEqualTo("RESOURCE_NOT_FOUND");
		}

	}

	// ===========================================================================
	// Helpers — HTTP wire format
	// ===========================================================================

	private ResponseEntity<String> doLogin(String tenantSlug, String email, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("X-Tenant-Slug", tenantSlug);
		String body = String.format(
				"{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		return rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private AuthResponse login(String tenantSlug, String email, String password) throws Exception {
		ResponseEntity<String> response = doLogin(tenantSlug, email, password);
		assertThat(response.getStatusCode())
				.as("seed login() helper requires a successful response")
				.isEqualTo(HttpStatus.OK);
		return objectMapper.readValue(response.getBody(), AuthResponse.class);
	}

	private ResponseEntity<String> doRefresh(String refreshToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String body = String.format("{\"refreshToken\":\"%s\"}", refreshToken);
		return rest.exchange(AUTH_BASE + "/refresh", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doLogout(String refreshToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		String body = String.format("{\"refreshToken\":\"%s\"}", refreshToken);
		return rest.exchange(AUTH_BASE + "/logout", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doGet(String path) {
		return rest.exchange(AUTH_BASE + path, HttpMethod.GET,
				HttpEntity.EMPTY, String.class);
	}

	private ResponseEntity<String> doGetWithBearer(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(AUTH_BASE + path, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
	}

	private void assertUnauthorizedWithCode(ResponseEntity<String> response, String expectedCode)
			throws Exception {
		assertThat(response.getStatusCode())
				.as("expected 401 with code %s; body=%s", expectedCode, response.getBody())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		JsonNode body = objectMapper.readTree(response.getBody());
		assertThat(body.get("errors").get(0).get("code").asText()).isEqualTo(expectedCode);
	}

	/**
	 * Decodes the JWT payload (middle segment) without verifying the signature.
	 * Adequate here because we only use it to read the routing claim
	 * ({@code tenant_id}); any tampering would be caught by the server when
	 * the token is presented back to it.
	 */
	private JsonNode decodeJwtPayload(String jwt) throws Exception {
		String[] parts = jwt.split("\\.");
		assertThat(parts).as("JWT must have 3 segments").hasSize(3);
		byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
		return objectMapper.readTree(payload);
	}

	// ===========================================================================
	// Helpers — DB seeding (tenants are global, users are tenant-scoped)
	// ===========================================================================

	/**
	 * Pair of tenants seeded with one user each, sharing {@link #SHARED_EMAIL}
	 * but with different passwords. This is the minimum fixture that exercises
	 * cross-tenant isolation.
	 */
	record TenantPair(Tenant tenantA, Tenant tenantB, User userA, User userB) {}

	private TenantPair seedTenantPair() {
		Tenant tenantA = createTenant("it-tenant-a-", TenantStatus.ACTIVE);
		Tenant tenantB = createTenant("it-tenant-b-", TenantStatus.ACTIVE);
		User userA = createUser(tenantA, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE);
		User userB = createUser(tenantB, SHARED_EMAIL, PASSWORD_B, UserStatus.ACTIVE);
		return new TenantPair(tenantA, tenantB, userA, userB);
	}

	private Tenant createTenant(String slugPrefix, TenantStatus status) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(status);
		// tenants is a global catalog; no TenantContext is required for INSERT.
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	/**
	 * Inserts a user inside a specific tenant. Hibernate's
	 * {@code TenantIdResolver} fires when the session is opened, so we wrap
	 * the persist call in {@code TenantContext.runAs} → {@code txTemplate.execute}
	 * to guarantee the resolver sees the right tenant id (the same ordering
	 * trick documented in {@code AuthServiceImpl}).
	 */
	private User createUser(Tenant tenant, String email, String rawPassword, UserStatus status) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash(passwordEncoder.encode(rawPassword));
					user.setFirstName("It");
					user.setLastName(tenant.getSlug());
					user.setStatus(status);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					return userRepository.saveAndFlush(user);
				}));
	}

}
