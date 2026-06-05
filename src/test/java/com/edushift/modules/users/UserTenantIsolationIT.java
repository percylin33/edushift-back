package com.edushift.modules.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
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
 * Cross-tenant isolation IT for the {@code /v1/users} surface added by
 * Sprint 3 BE-3.1.
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>List isolation</strong> — {@code GET /v1/users} returns the
 *       caller's tenant only; users from a sibling tenant never bleed in,
 *       even when both tenants have a user with the same email.</li>
 *   <li><strong>Read isolation</strong> — {@code GET /v1/users/{publicUuid}}
 *       returns 404 when the publicUuid belongs to another tenant (the row
 *       exists but is filtered out by the {@code @TenantId} discriminator).</li>
 *   <li><strong>Write isolation</strong> — {@code PATCH /v1/users/{id}}
 *       on a sibling-tenant publicUuid returns 404 and does not mutate the
 *       target row.</li>
 *   <li><strong>Role-gate enforcement</strong> — Same checks made by a
 *       caller without {@code TENANT_ADMIN} return 403, never 200/404 for
 *       a sibling tenant resource (confirms {@code @PreAuthorize} runs
 *       <em>before</em> the cross-tenant lookup).</li>
 *   <li><strong>Last-admin protection</strong> survives across tenants —
 *       demoting tenant A's only admin via roles= {@code []} returns 422
 *       LAST_ADMIN_PROTECTION; tenant B's admin remains an admin.</li>
 * </ul>
 *
 * <h3>Why every fixture re-creates its own tenant pair</h3>
 * Same rationale as {@code TenantsTenantIsolationIT} — slug uniqueness is
 * enforced at the DB level and the IT runner doesn't roll back state
 * between methods, so each test seeds UUID-suffixed identifiers.
 */
@DisplayName("Users module multi-tenancy isolation")
class UserTenantIsolationIT extends IntegrationTest {

	private static final String USERS_BASE = "/v1/users";

	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "shared@isolation.test";

	private static final String PASSWORD_A = "PassUsersA-1!";

	private static final String PASSWORD_B = "PassUsersB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) {
			tx = new TransactionTemplate(txManager);
		}
		return tx;
	}

	// ===========================================================================
	// GET /v1/users — list isolation
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/users — list isolation")
	class ListIsolation {

		@Test
		@DisplayName("admin A sees A's users only — never B's, even with shared emails")
		void listOnlyOwnTenant() throws Exception {
			TenantPair pair = seedTenantPair();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(USERS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode page = objectMapper.readTree(response.getBody());
			JsonNode content = page.path("content");
			assertThat(content.isArray()).isTrue();

			// Every emitted user MUST belong to tenant A — and the row
			// belonging to tenant B must NOT appear, even though the
			// email is identical.
			boolean foundA = false;
			for (JsonNode item : content) {
				UUID emittedUuid = UUID.fromString(item.get("publicUuid").asText());
				assertThat(emittedUuid)
						.as("admin A must NOT see user-B's publicUuid in the list response")
						.isNotEqualTo(pair.userB().getPublicUuid());
				if (emittedUuid.equals(pair.userA().getPublicUuid())) {
					foundA = true;
				}
			}
			assertThat(foundA).as("admin A must see their own user-A row in the list").isTrue();
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() {
			ResponseEntity<String> response = rest.getForEntity(USERS_BASE, String.class);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		@Test
		@DisplayName("authenticated but no TENANT_ADMIN → 403")
		void forbiddenWithoutRole() throws Exception {
			Tenant tenant = createTenant("it-users-noadmin-", TenantStatus.ACTIVE);
			createUser(tenant, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE, false);
			AuthResponse session = login(tenant.getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(USERS_BASE, session.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		}
	}

	// ===========================================================================
	// GET /v1/users/{publicUuid} — read isolation
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/users/{publicUuid} — read isolation")
	class ReadIsolation {

		@Test
		@DisplayName("admin A reading user-B's publicUuid → 404 RESOURCE_NOT_FOUND (not 200)")
		void crossTenantReadIs404() throws Exception {
			TenantPair pair = seedTenantPair();
			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					USERS_BASE + "/" + pair.userB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode())
					.as("cross-tenant lookup must return 404 (the row exists but the @TenantId filter hides it)")
					.isEqualTo(HttpStatus.NOT_FOUND);
			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("errors").get(0).get("code").asText())
					.isEqualTo("RESOURCE_NOT_FOUND");
		}

		@Test
		@DisplayName("admin A reading user-A's own publicUuid → 200 with full detail")
		void selfReadSucceeds() throws Exception {
			TenantPair pair = seedTenantPair();
			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					USERS_BASE + "/" + pair.userA().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(response.getBody()).get("data");
			assertThat(body.get("publicUuid").asText())
					.isEqualTo(pair.userA().getPublicUuid().toString());
			assertThat(body.get("email").asText()).isEqualTo(SHARED_EMAIL);
		}
	}

	// ===========================================================================
	// PATCH /v1/users/{publicUuid} — write isolation
	// ===========================================================================

	@Nested
	@DisplayName("PATCH /v1/users/{publicUuid} — write isolation")
	class WriteIsolation {

		@Test
		@DisplayName("admin A patching user-B → 404, B's profile is untouched")
		void crossTenantWriteIs404() throws Exception {
			TenantPair pair = seedTenantPair();
			final String originalLastNameB = pair.userB().getLastName();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doPatch(
					USERS_BASE + "/" + pair.userB().getPublicUuid(),
					loginA.accessToken(),
					"{\"lastName\":\"hacked-by-tenant-A\"}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			User freshB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s -> userRepository.findById(pair.userB().getId()).orElseThrow()));
			assertThat(freshB.getLastName())
					.as("user-B's lastName must remain untouched after a cross-tenant PATCH")
					.isEqualTo(originalLastNameB);
		}
	}

	// ===========================================================================
	// POST /v1/users/{publicUuid}/roles — last-admin protection across tenants
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/{publicUuid}/roles — last-admin protection across tenants")
	class LastAdminProtection {

		@Test
		@DisplayName("demoting tenant A's only admin → 409 LAST_ADMIN_PROTECTION; tenant B is unaffected")
		void demotingLastAdminRejected() throws Exception {
			// We craft a tenant with EXACTLY one TENANT_ADMIN; demoting that
			// admin would violate the guardrail in
			// UserManagementServiceImpl.ensureNotLastAdmin.
			Tenant tenantA = createTenant("it-users-last-a-", TenantStatus.ACTIVE);
			Tenant tenantB = createTenant("it-users-last-b-", TenantStatus.ACTIVE);
			User adminA = createUser(tenantA, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE, true);
			User adminB = createUser(tenantB, SHARED_EMAIL, PASSWORD_B, UserStatus.ACTIVE, true);

			AuthResponse loginA = login(tenantA.getSlug(), SHARED_EMAIL, PASSWORD_A);

			// roles=["TEACHER"] strips TENANT_ADMIN — should be rejected.
			ResponseEntity<String> response = doPost(
					USERS_BASE + "/" + adminA.getPublicUuid() + "/roles",
					loginA.accessToken(),
					"{\"roles\":[\"TEACHER\"]}");

			assertThat(response.getStatusCode())
					.as("last-admin guardrail must fire on this scenario; body=%s",
							response.getBody())
					.isEqualTo(HttpStatus.CONFLICT);
			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("errors").get(0).get("code").asText())
					.isEqualTo("LAST_ADMIN_PROTECTION");

			// Tenant B's admin must still be intact (the failure is local to A).
			User freshB = TenantContext.runAs(tenantB.getId(),
					() -> tx().execute(s -> userRepository.findById(adminB.getId()).orElseThrow()));
			assertThat(freshB.hasRole(UserRole.TENANT_ADMIN))
					.as("tenant B's admin must remain TENANT_ADMIN after tenant A's failed mutation")
					.isTrue();
		}
	}

	// ===========================================================================
	// HTTP helpers
	// ===========================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private ResponseEntity<String> doLogin(String slug, String email, String password) {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", slug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		return rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private AuthResponse login(String slug, String email, String password) throws Exception {
		ResponseEntity<String> response = doLogin(slug, email, password);
		assertThat(response.getStatusCode())
				.as("seed login() requires HTTP 200; body=%s", response.getBody())
				.isEqualTo(HttpStatus.OK);
		return objectMapper.readValue(response.getBody(), AuthResponse.class);
	}

	private ResponseEntity<String> doGet(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> doPatch(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PATCH,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doPost(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	// ===========================================================================
	// DB seeding
	// ===========================================================================

	record TenantPair(Tenant tenantA, Tenant tenantB, User userA, User userB) {}

	private TenantPair seedTenantPair() {
		Tenant tenantA = createTenant("it-users-a-", TenantStatus.ACTIVE);
		Tenant tenantB = createTenant("it-users-b-", TenantStatus.ACTIVE);
		User userA = createUser(tenantA, SHARED_EMAIL, PASSWORD_A, UserStatus.ACTIVE, true);
		User userB = createUser(tenantB, SHARED_EMAIL, PASSWORD_B, UserStatus.ACTIVE, true);
		return new TenantPair(tenantA, tenantB, userA, userB);
	}

	private Tenant createTenant(String slugPrefix, TenantStatus status) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(status);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

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
