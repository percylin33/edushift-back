package com.edushift.modules.tenants;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
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
 * End-to-end integration test for the tenants module. Mirrors the structure
 * of {@code AuthTenantIsolationIT} (Sprint 1) and proves the guarantees that
 * Sprint 2 adds on top:
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — {@code GET /tenants/me} returns the
 *       caller's tenant only, never another tenant's data, even when both
 *       are active and reachable from the same database.</li>
 *   <li><strong>Public surface stays public</strong> —
 *       {@code GET /tenants/by-slug/{slug}} works without a bearer (the
 *       login screen needs branding before any user authenticates).</li>
 *   <li><strong>Write isolation + role gate</strong> —
 *       {@code PATCH /tenants/me} mutates only the caller's tenant, and is
 *       rejected with 403 when the bearer doesn't carry
 *       {@code TENANT_ADMIN}.</li>
 *   <li><strong>Self-signup</strong> — {@code POST /tenants/register}
 *       creates a tenant + admin atomically, returns a session whose JWT
 *       already carries {@code TENANT_ADMIN}, and rejects duplicate slugs
 *       with HTTP 409.</li>
 * </ul>
 *
 * <h3>Why every fixture re-creates its own tenant pair</h3>
 * Slug uniqueness is enforced at the DB level, so any cross-test reuse
 * would couple ordering and break parallel execution. UUID-suffixed slugs
 * keep the tests independent and the DB clean across runs.
 */
@DisplayName("Tenants module multi-tenancy isolation")
class TenantsTenantIsolationIT extends IntegrationTest {

	/** Same caveat as {@code AuthTenantIsolationIT}: the {@code /api} context-path is auto-prepended. */
	private static final String TENANTS_BASE = "/v1/tenants";

	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "admin@isolation.test";

	private static final String PASSWORD_A = "PassTenantA-1!";

	private static final String PASSWORD_B = "PassTenantB-2!";

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
	// Public surface — anyone can read /by-slug for the login screen branding
	// ===========================================================================

	@Nested
	@DisplayName("Public lookup by slug")
	class PublicBySlug {

		@Test
		@DisplayName("GET /by-slug/{slug} returns the public summary without authentication")
		void publicLookupSucceedsWithoutBearer() throws Exception {
			Tenant tenant = createTenant("it-public-", TenantStatus.ACTIVE);

			ResponseEntity<String> response = rest.getForEntity(
					TENANTS_BASE + "/by-slug/" + tenant.getSlug(), String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.get("slug").asText()).isEqualTo(tenant.getSlug());
			assertThat(data.get("name").asText()).isEqualTo(tenant.getName());
			// TenantSummary deliberately hides plan/settings/featureFlags from
			// the public surface — see TenantSummary javadoc for the rationale.
			assertThat(data.has("plan")).as("plan must NOT be in the public summary").isFalse();
			assertThat(data.has("settings")).as("settings must NOT be in the public summary").isFalse();
			assertThat(data.has("featureFlags")).as("featureFlags must NOT be in the public summary").isFalse();
		}

		@Test
		@DisplayName("GET /by-slug/{unknown} returns 404")
		void unknownSlugReturns404() {
			ResponseEntity<String> response = rest.getForEntity(
					TENANTS_BASE + "/by-slug/" + "ghost-" + UUID.randomUUID().toString().substring(0, 8),
					String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

	}

	// ===========================================================================
	// Read isolation — bearer A sees A only, never B
	// ===========================================================================

	@Nested
	@DisplayName("GET /tenants/me bearer routing")
	class ReadIsolation {

		@Test
		@DisplayName("/me with bearerA returns tenantA — never tenantB (even when both have a TENANT_ADMIN)")
		void meReturnsBearerOwnerTenant() throws Exception {
			TenantPair pair = seedTenantPair();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGetWithBearer(TENANTS_BASE + "/me", loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");

			assertThat(data.get("publicUuid").asText())
					.as("/me must return tenant A's publicUuid (not tenant B's)")
					.isEqualTo(pair.tenantA().getPublicUuid().toString())
					.isNotEqualTo(pair.tenantB().getPublicUuid().toString());
			assertThat(data.get("slug").asText()).isEqualTo(pair.tenantA().getSlug());
			// Authenticated callers DO see plan/settings/featureFlags.
			assertThat(data.has("plan")).isTrue();
			assertThat(data.has("settings")).isTrue();
			assertThat(data.has("featureFlags")).isTrue();
		}

		@Test
		@DisplayName("/me without bearer returns 401 (entry point)")
		void meWithoutBearerReturns401() {
			ResponseEntity<String> response = rest.getForEntity(TENANTS_BASE + "/me", String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

	}

	// ===========================================================================
	// Write isolation + @PreAuthorize role gate
	// ===========================================================================

	@Nested
	@DisplayName("PATCH /tenants/me — write isolation + role gate")
	class WriteIsolationAndRoleGate {

		@Test
		@DisplayName("PATCH with bearerA mutates ONLY tenant A — tenant B is untouched")
		void patchMutatesOnlyOwnTenant() throws Exception {
			TenantPair pair = seedTenantPair();
			final String originalNameB = pair.tenantB().getName();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			final String newNameForA = "tenant A renamed via PATCH " + UUID.randomUUID();
			ResponseEntity<String> patchResponse = doPatchWithBearer(
					TENANTS_BASE + "/me",
					loginA.accessToken(),
					"{\"name\":\"" + newNameForA + "\"}");

			assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

			// Verify directly via repository that B did NOT mutate.
			Tenant freshA = tx().execute(s -> tenantRepository.findById(pair.tenantA().getId()).orElseThrow());
			Tenant freshB = tx().execute(s -> tenantRepository.findById(pair.tenantB().getId()).orElseThrow());

			assertThat(freshA.getName()).isEqualTo(newNameForA);
			assertThat(freshB.getName())
					.as("tenant B must remain untouched by a PATCH that targets A")
					.isEqualTo(originalNameB);
		}

		@Test
		@DisplayName("PATCH without TENANT_ADMIN returns 403 FORBIDDEN")
		void patchWithoutRoleReturns403() throws Exception {
			Tenant tenant = createTenant("it-noadmin-", TenantStatus.ACTIVE);
			// User exists, can authenticate, but has NO roles assigned.
			createUser(tenant, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE, /* tenantAdmin */ false);

			AuthResponse session = login(tenant.getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doPatchWithBearer(
					TENANTS_BASE + "/me",
					session.accessToken(),
					"{\"name\":\"this should not stick\"}");

			assertThat(response.getStatusCode())
					.as("@PreAuthorize must reject callers without TENANT_ADMIN")
					.isEqualTo(HttpStatus.FORBIDDEN);
			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("errors").get(0).get("code").asText()).isEqualTo("FORBIDDEN");
		}

	}

	// ===========================================================================
	// Self-signup — POST /tenants/register
	// ===========================================================================

	@Nested
	@DisplayName("POST /tenants/register — self-signup")
	class SelfSignup {

		@Test
		@DisplayName("creates tenant + admin atomically and returns a session with TENANT_ADMIN")
		void registerCreatesTenantAndAdminWithRole() throws Exception {
			final String newSlug = "it-signup-" + UUID.randomUUID().toString().substring(0, 8);
			final String adminEmail = "owner@" + newSlug + ".test";

			ResponseEntity<String> response = rest.exchange(
					TENANTS_BASE + "/register",
					HttpMethod.POST,
					new HttpEntity<>(buildRegisterBody(newSlug, adminEmail), jsonHeaders()),
					String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("accessToken").asText()).isNotBlank();
			assertThat(body.get("refreshToken").asText()).isNotBlank();
			assertThat(body.get("user").get("email").asText()).isEqualTo(adminEmail);

			// Decode the JWT and verify the role claim made it through.
			JsonNode claims = decodeJwtPayload(body.get("accessToken").asText());
			assertThat(claims.get("tenant_slug").asText()).isEqualTo(newSlug);
			JsonNode rolesClaim = claims.get("roles");
			assertThat(rolesClaim.isArray()).isTrue();
			boolean hasTenantAdmin = false;
			for (JsonNode r : rolesClaim) {
				if ("TENANT_ADMIN".equals(r.asText())) hasTenantAdmin = true;
			}
			assertThat(hasTenantAdmin)
					.as("self-signup MUST issue a JWT carrying TENANT_ADMIN")
					.isTrue();

			// Verify the persisted state matches the contract.
			Tenant persistedTenant = tx().execute(s ->
					tenantRepository.findBySlugIgnoreCase(newSlug).orElseThrow());
			assertThat(persistedTenant.getStatus()).isEqualTo(TenantStatus.PENDING);
			assertThat(persistedTenant.getPlan()).isEqualTo(TenantPlan.TRIAL);
			assertThat(persistedTenant.getTrialEndsAt())
					.as("self-signup must seed a trial window")
					.isNotNull();
		}

		@Test
		@DisplayName("rejects a duplicate slug with HTTP 409 TENANT_SLUG_TAKEN")
		void registerRejectsDuplicateSlug() throws Exception {
			final String taken = "it-dup-" + UUID.randomUUID().toString().substring(0, 8);

			// First registration succeeds.
			ResponseEntity<String> first = rest.exchange(
					TENANTS_BASE + "/register", HttpMethod.POST,
					new HttpEntity<>(buildRegisterBody(taken, "first@" + taken + ".test"), jsonHeaders()),
					String.class);
			assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

			// Second registration with the same slug — must be rejected.
			ResponseEntity<String> second = rest.exchange(
					TENANTS_BASE + "/register", HttpMethod.POST,
					new HttpEntity<>(buildRegisterBody(taken, "second@" + taken + ".test"), jsonHeaders()),
					String.class);

			assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			JsonNode err = objectMapper.readTree(second.getBody()).get("errors").get(0);
			assertThat(err.get("code").asText()).isEqualTo("TENANT_SLUG_TAKEN");
		}

	}

	// ===========================================================================
	// Helpers — HTTP wire format
	// ===========================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private ResponseEntity<String> doLogin(String tenantSlug, String email, String password) {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", tenantSlug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		return rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private AuthResponse login(String tenantSlug, String email, String password) throws Exception {
		ResponseEntity<String> response = doLogin(tenantSlug, email, password);
		assertThat(response.getStatusCode())
				.as("login() helper requires HTTP 200; body=%s", response.getBody())
				.isEqualTo(HttpStatus.OK);
		return objectMapper.readValue(response.getBody(), AuthResponse.class);
	}

	private ResponseEntity<String> doGetWithBearer(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> doPatchWithBearer(String path, String bearer, String jsonBody) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PATCH,
				new HttpEntity<>(jsonBody, headers), String.class);
	}

	private String buildRegisterBody(String slug, String adminEmail) {
		return "{"
				+ "\"tenantName\":\"IT Tenant " + slug + "\","
				+ "\"tenantSlug\":\"" + slug + "\","
				+ "\"adminEmail\":\"" + adminEmail + "\","
				+ "\"adminPassword\":\"" + PASSWORD_A + "\","
				+ "\"adminFirstName\":\"It\","
				+ "\"adminLastName\":\"Owner\""
				+ "}";
	}

	/** See {@code AuthTenantIsolationIT} for the rationale (signature unverified, claim-only). */
	private JsonNode decodeJwtPayload(String jwt) throws Exception {
		String[] parts = jwt.split("\\.");
		assertThat(parts).as("JWT must have 3 segments").hasSize(3);
		byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
		return objectMapper.readTree(payload);
	}

	// ===========================================================================
	// Helpers — DB seeding
	// ===========================================================================

	record TenantPair(Tenant tenantA, Tenant tenantB, User userA, User userB) {}

	/**
	 * Two ACTIVE tenants, one TENANT_ADMIN each (so PATCH passes
	 * {@code @PreAuthorize} when called with either bearer). Same email on
	 * both users intentionally — surfaces any cross-tenant lookup bug as a
	 * test failure.
	 */
	private TenantPair seedTenantPair() {
		Tenant tenantA = createTenant("it-ta-", TenantStatus.ACTIVE);
		Tenant tenantB = createTenant("it-tb-", TenantStatus.ACTIVE);
		User userA = createUser(tenantA, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE, /* tenantAdmin */ true);
		User userB = createUser(tenantB, SHARED_EMAIL, PASSWORD_B, UserStatus.ACTIVE, /* tenantAdmin */ true);
		return new TenantPair(tenantA, tenantB, userA, userB);
	}

	private Tenant createTenant(String slugPrefix, TenantStatus status) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(status);
		// No TenantContext: tenants is the global catalog.
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	/**
	 * Inserts a user inside a specific tenant. {@code tenantAdmin=true}
	 * grants {@link UserRole#TENANT_ADMIN} so PATCH can pass the role gate.
	 */
	private User createUser(Tenant tenant, String email, String rawPassword,
	                        UserStatus status, boolean tenantAdmin) {
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
					if (tenantAdmin) {
						user.addRole(UserRole.TENANT_ADMIN);
					}
					return userRepository.saveAndFlush(user);
				}));
	}

}
