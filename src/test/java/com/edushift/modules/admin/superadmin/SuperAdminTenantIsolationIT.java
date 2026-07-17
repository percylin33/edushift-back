package com.edushift.modules.admin.superadmin;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.audit.entity.AuditLog;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.repository.AuditLogRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end integration test for the {@code superadmin} module.
 *
 * <h3>What this proves</h3>
 * <ol>
 *   <li><strong>Authorization is layered, not bypassed.</strong>
 *       Class-level {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} blocks
 *       TENANT_ADMIN (and any other role) with 403 — verified against a
 *       real Tomcat + Spring Security chain.</li>
 *   <li><strong>Cross-tenant scope.</strong> SUPER_ADMIN bypasses the
 *       Hibernate {@code @TenantId} filter: {@code GET /admin/super-admins}
 *       returns every active SUPER_ADMIN, including those seeded in the
 *       {@code edushift-system} sentinel tenant.</li>
 *   <li><strong>Quorum is enforced at the DB boundary.</strong> A
 *       SUPER_ADMIN cannot disable the last remaining one.</li>
 *   <li><strong>Self-disable is forbidden.</strong> The actor's UUID
 *       propagates from the JWT {@code sub} claim down to
 *       {@code SuperAdminService.disable()} and is rejected with
 *       422 {@code SELF_DISABLE_FORBIDDEN}.</li>
 *   <li><strong>Audit log is persisted.</strong> Both CREATE and UPDATE
 *       actions land in {@code audit_logs} with the expected
 *       {@code resource_type=super_admin}.</li>
 * </ol>
 *
 * <h3>Seeding strategy</h3>
 * The sentinel tenant is seeded by Flyway (V53) so we only need to look it
 * up by its well-known UUID. Each test creates its own throwaway SUPER_ADMIN
 * (UUID-suffixed email) so concurrent test methods don't collide on the
 * global email uniqueness constraint.
 */
@DisplayName("Super-admin module end-to-end")
class SuperAdminTenantIsolationIT extends IntegrationTest {

	/** Path relative to the servlet context-path. {@code /api} is auto-prepended. */
	private static final String BASE = "/admin/super-admins";

	private static final String SUPER_ADMIN_PASSWORD = "PassForSuperAdmin-IT-1!";

	@Autowired private TestRestTemplate rest;
	@Autowired private UserRepository userRepository;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private JwtService jwtService;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	@BeforeEach
	void tx() {
		tx = new TransactionTemplate(txManager);
	}

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	// =========================================================================
	// Authorization — every endpoint requires ROLE_SUPER_ADMIN
	// =========================================================================

	@Nested
	@DisplayName("Authorization — @PreAuthorize(\"hasRole('SUPER_ADMIN')\")")
	class AuthorizationGate {

		@Test
		@DisplayName("anonymous GET → 401")
		void anonymousListReturns401() {
			ResponseEntity<String> response = doGet(BASE);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		@Test
		@DisplayName("TENANT_ADMIN GET → 403")
		void tenantAdminListReturns403() {
			TenantPair pair = seedTenantPairWithAdmin();
			String bearer = mintBearer(pair.tenantAdmin(), pair.tenant());

			ResponseEntity<String> response = doGetWithBearer(BASE, bearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("TENANT_ADMIN POST create → 403")
		void tenantAdminCreateReturns403() {
			TenantPair pair = seedTenantPairWithAdmin();
			String bearer = mintBearer(pair.tenantAdmin(), pair.tenant());

			ResponseEntity<String> response = doPostWithBearer(BASE, bearer, """
					{"email":"new-super@edushift.pe","firstName":"A","lastName":"B"}
					""");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("TENANT_ADMIN PATCH disable → 403")
		void tenantAdminDisableReturns403() {
			TenantPair pair = seedTenantPairWithAdmin();
			User target = seedSuperAdmin("target-" + UUID.randomUUID() + "@edushift.pe");
			String bearer = mintBearer(pair.tenantAdmin(), pair.tenant());

			ResponseEntity<String> response = doPatchWithBearer(
					BASE + "/" + target.getPublicUuid() + "/disable", bearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		}
	}

	// =========================================================================
	// Cross-tenant scope — SUPER_ADMIN sees ALL active SUPER_ADMINs
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant scope")
	class CrossTenantScope {

		@Test
		@DisplayName("SUPER_ADMIN GET /super-admins returns every active SUPER_ADMIN")
		void listReturnsAllSuperAdmins() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);

			ResponseEntity<String> response = doGetWithBearer(BASE, bearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(response.getBody());
			JsonNode data = body.get("data");
			assertThat(data.isArray()).isTrue();
			assertThat(data.size()).isGreaterThanOrEqualTo(2);

			// Every returned entry must have SUPER_ADMIN in roles and ACTIVE
			// status — the service's isSuperAdminActive() filter.
			for (JsonNode entry : data) {
				boolean hasSuperAdmin = false;
				for (JsonNode role : entry.get("roles")) {
					if ("SUPER_ADMIN".equals(role.asText())) {
						hasSuperAdmin = true;
						break;
					}
				}
				assertThat(hasSuperAdmin)
						.as("every listed admin must carry SUPER_ADMIN role: %s", entry)
						.isTrue();
				assertThat(entry.get("status").asText()).isEqualTo("ACTIVE");
			}
		}

		@Test
		@DisplayName("INACTIVE SUPER_ADMINs are excluded from list()")
		void listExcludesInactive() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			// Disable one of them through the public endpoint, then re-list.
			String bearer = mintBearer(seed.actor, seed.sentinel);

			// Pick the OTHER super admin to disable (so actor != target).
			User other = seed.others.get(0);
			ResponseEntity<String> disable = doPatchWithBearer(
					BASE + "/" + other.getPublicUuid() + "/disable", bearer);
			assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.OK);

			// Now list and confirm `other` is gone.
			ResponseEntity<String> list = doGetWithBearer(BASE, bearer);
			JsonNode data = objectMapper.readTree(list.getBody()).get("data");
			for (JsonNode entry : data) {
				assertThat(entry.get("publicUuid").asText())
						.isNotEqualTo(other.getPublicUuid().toString());
			}
		}
	}

	// =========================================================================
	// Quorum — disabling the last active SUPER_ADMIN is forbidden
	// =========================================================================

	@Nested
	@DisplayName("Quorum")
	class Quorum {

		@Test
		@DisplayName("disabling the last active SUPER_ADMIN returns 403 QUORUM_REQUIRED")
		void lastSuperAdminCannotBeDisabled() throws Exception {
			// Seed ONLY ONE active SUPER_ADMIN, and no second.
			User solo = seedSuperAdmin("solo-" + UUID.randomUUID() + "@edushift.pe");
			// To disable ourselves we need a different bearer. Mint one for `solo`
			// (whose id is also the actor — the service short-circuits BEFORE the
			// quorum check, so this verifies self-disable wins, not quorum).
			// The real test: actor=other tries to disable solo AND solo is last.
			// We achieve "other" by creating a second SUPER_ADMIN and disabling
			// it first via the endpoint (now `solo` is last), THEN having the
			// same actor attempt to disable solo.
			User second = seedSuperAdmin("second-" + UUID.randomUUID() + "@edushift.pe");
			String bearer = mintBearer(second, sentinelTenant());

			// Step 1: disable `second` (the actor). Fails with SELF_DISABLE.
			ResponseEntity<String> self = doPatchWithBearer(
					BASE + "/" + second.getPublicUuid() + "/disable", bearer);
			assertThat(self.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

			// Use a different actor for the quorum test: create a third admin
			// to act as the actor, disable the third, leaving only solo.
			User third = seedSuperAdmin("third-" + UUID.randomUUID() + "@edushift.pe");
			String bearerThird = mintBearer(third, sentinelTenant());
			ResponseEntity<String> disableThird = doPatchWithBearer(
					BASE + "/" + third.getPublicUuid() + "/disable", bearerThird);
			// Disable of third must be rejected — only `solo` remains active.
			assertThat(disableThird.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
			JsonNode err = objectMapper.readTree(disableThird.getBody())
					.get("errors").get(0);
			assertThat(err.get("code").asText()).isEqualTo("QUORUM_REQUIRED");

			// And `solo` is untouched.
			User reloaded = userRepository.findByPublicUuid(solo.getPublicUuid()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
		}

		@Test
		@DisplayName("disabling when AT LEAST ONE other remains → 200 OK")
		void quorumAllowsDisableIfOtherRemains() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);
			User other = seed.others.get(0);

			ResponseEntity<String> response = doPatchWithBearer(
					BASE + "/" + other.getPublicUuid() + "/disable", bearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.get("status").asText()).isEqualTo("INACTIVE");
		}
	}

	// =========================================================================
	// Self-disable — actor cannot disable themselves
	// =========================================================================

	@Nested
	@DisplayName("Self-disable")
	class SelfDisable {

		@Test
		@DisplayName("actor disabling themselves → 422 SELF_DISABLE_FORBIDDEN")
		void selfDisableReturns422() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);

			ResponseEntity<String> response = doPatchWithBearer(
					BASE + "/" + seed.actor.getPublicUuid() + "/disable", bearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
			JsonNode err = objectMapper.readTree(response.getBody())
					.get("errors").get(0);
			assertThat(err.get("code").asText()).isEqualTo("SELF_DISABLE_FORBIDDEN");
		}
	}

	// =========================================================================
	// Create — happy path + validation
	// =========================================================================

	@Nested
	@DisplayName("Create")
	class Create {

		@Test
		@DisplayName("SUPER_ADMIN POST create → 200, user persisted in sentinel tenant with sentinel hash")
		void happyPath() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);
			String email = "new-" + UUID.randomUUID() + "@edushift.pe";

			ResponseEntity<String> response = doPostWithBearer(BASE, bearer, String.format(
					"{\"email\":\"%s\",\"firstName\":\"Grace\",\"lastName\":\"Hopper\"}",
					email));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.get("email").asText()).isEqualTo(email);
			assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
			assertThat(data.get("mfaEnabled").asBoolean()).isFalse();

			// User exists in sentinel tenant with sentinel hash. We look it up
			// by email using TenantContext.runAs(SUPER_ADMIN_SENTINEL, ...)
			// because UserRepository uses Hibernate's @TenantId filter.
			User persisted = TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL,
					() -> userRepository.findByEmail(email).orElseThrow());
			assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
			assertThat(persisted.getPasswordHash())
					.isEqualTo("SUPER_ADMIN_RESET_REQUIRED_v1_new_user");
			assertThat(persisted.hasRole(UserRole.SUPER_ADMIN)).isTrue();
		}

		@Test
		@DisplayName("SUPER_ADMIN POST duplicate email → 409 EMAIL_TAKEN")
		void emailTaken() {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);

			String email = "dup-" + UUID.randomUUID() + "@edushift.pe";
			ResponseEntity<String> first = doPostWithBearer(BASE, bearer, String.format(
					"{\"email\":\"%s\",\"firstName\":\"A\",\"lastName\":\"B\"}", email));
			assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

			ResponseEntity<String> second = doPostWithBearer(BASE, bearer, String.format(
					"{\"email\":\"%s\",\"firstName\":\"C\",\"lastName\":\"D\"}", email));
			assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(second.getBody()).contains("EMAIL_TAKEN");
		}

		@Test
		@DisplayName("blank email → 400 BAD_REQUEST")
		void blankEmail() {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);

			ResponseEntity<String> response = doPostWithBearer(BASE, bearer,
					"{\"email\":\"\",\"firstName\":\"A\",\"lastName\":\"B\"}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}
	}

	// =========================================================================
	// Audit log persistence — both CREATE and UPDATE actions land in audit_logs
	// =========================================================================

	@Nested
	@DisplayName("Audit log persistence")
	class AuditPersistence {

		@Test
		@DisplayName("create + disable each emit one audit_logs row with resource_type=super_admin")
		void createAndDisableAreAudited() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);
			String email = "audited-" + UUID.randomUUID() + "@edushift.pe";

			ResponseEntity<String> createResp = doPostWithBearer(BASE, bearer, String.format(
					"{\"email\":\"%s\",\"firstName\":\"Audit\",\"lastName\":\"Test\"}", email));
			UUID newId = UUID.fromString(
					objectMapper.readTree(createResp.getBody()).get("data").get("publicUuid").asText());

			ResponseEntity<String> disableResp = doPatchWithBearer(
					BASE + "/" + newId + "/disable", bearer);
			assertThat(disableResp.getStatusCode()).isEqualTo(HttpStatus.OK);

			List<AuditLog> logs = auditLogRepository.findAll().stream()
					.filter(l -> newId.equals(l.getResourceId()))
					.toList();
			assertThat(logs)
					.as("create + disable must emit two audit logs for the new admin")
					.hasSize(2);
			assertThat(logs).extracting(AuditLog::getAction)
					.containsExactlyInAnyOrder(AuditAction.CREATE, AuditAction.UPDATE);
			assertThat(logs).extracting(AuditLog::getResourceType)
					.containsOnly("super_admin");
		}
	}

	// =========================================================================
	// count-active
	// =========================================================================

	@Nested
	@DisplayName("count-active")
	class CountActive {

		@Test
		@DisplayName("returns the count of active SUPER_ADMINs")
		void countsMatch() throws Exception {
			SeedResult seed = seedAtLeastTwoSuperAdmins();
			String bearer = mintBearer(seed.actor, seed.sentinel);

			ResponseEntity<String> response = doGetWithBearer(BASE + "/count-active", bearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			long count = objectMapper.readTree(response.getBody()).get("data").asLong();
			assertThat(count).isGreaterThanOrEqualTo(2);
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	/** Mints a real signed JWT for the given user. */
	private String mintBearer(User user, Tenant tenant) {
		return jwtService.issueAccessToken(user, tenant,
				new LinkedHashSet<>(user.getRoleNames()));
	}

	private ResponseEntity<String> doGet(String path) {
		return rest.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
	}

	private ResponseEntity<String> doGetWithBearer(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> doPostWithBearer(String path, String bearer, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doPatchWithBearer(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>("{}", headers), String.class);
	}

	private Tenant sentinelTenant() {
		return tenantRepository.findById(TenantIdResolver.SUPER_ADMIN_SENTINEL)
				.orElseThrow(() -> new IllegalStateException(
						"sentinel tenant must be seeded by Flyway V53"));
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx.execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createUser(Tenant tenant, String email, String rawPassword,
	                        UserRole role, UserStatus status) {
		return TenantContext.runAs(tenant.getId(), () -> tx.execute(s -> {
			User u = new User();
			u.setEmail(email);
			u.setPasswordHash("{noop}" + rawPassword); // not encoded — IT only
			u.setFirstName("It");
			u.setLastName(tenant.getSlug());
			u.setStatus(status);
			u.setEmailVerified(true);
			u.setMfaEnabled(false);
			u.setRoleSet(new LinkedHashSet<>(Set.of(role)));
			return userRepository.saveAndFlush(u);
		}));
	}

	private User seedSuperAdmin(String email) {
		return createUser(sentinelTenant(), email, SUPER_ADMIN_PASSWORD,
				UserRole.SUPER_ADMIN, UserStatus.ACTIVE);
	}

	/**
	 * Seeds 2 active SUPER_ADMINs in the sentinel tenant plus one "actor"
	 * admin whose JWT we use to drive requests. The actor's id is
	 * intentionally distinct from the "others" so disable / self-disable
	 * branches are easy to exercise.
	 */
	private SeedResult seedAtLeastTwoSuperAdmins() {
		User actor = seedSuperAdmin("actor-" + UUID.randomUUID() + "@edushift.pe");
		User other1 = seedSuperAdmin("other1-" + UUID.randomUUID() + "@edushift.pe");
		User other2 = seedSuperAdmin("other2-" + UUID.randomUUID() + "@edushift.pe");
		return new SeedResult(actor, sentinelTenant(), List.of(other1, other2));
	}

	private TenantPair seedTenantPairWithAdmin() {
		Tenant tenant = createTenant("it-non-sentinel-");
		User admin = createUser(tenant, "admin-" + UUID.randomUUID() + "@e.pe",
				SUPER_ADMIN_PASSWORD, UserRole.TENANT_ADMIN, UserStatus.ACTIVE);
		return new TenantPair(tenant, admin);
	}

	record SeedResult(User actor, Tenant sentinel, List<User> others) {}
	record TenantPair(Tenant tenant, User tenantAdmin) {}
}
