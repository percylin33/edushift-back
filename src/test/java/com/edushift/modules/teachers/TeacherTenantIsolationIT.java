package com.edushift.modules.teachers;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * Cross-tenant isolation IT for {@code /v1/teachers} (Sprint 4 / BE-4.6).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li>Read isolation: A only sees A's teachers.</li>
 *   <li>Cross-tenant GET / DELETE / link-user → 404.</li>
 *   <li>Same documentNumber works in both tenants without colliding.</li>
 *   <li>Cross-tenant link-user reference → 404.</li>
 * </ul>
 */
@DisplayName("Teacher multi-tenancy isolation")
class TeacherTenantIsolationIT extends IntegrationTest {

	private static final String TEACHERS_BASE = "/v1/teachers";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-teacher@isolation.test";
	private static final String PASSWORD_A = "PassTeachA-1!";
	private static final String PASSWORD_B = "PassTeachB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Read isolation
	// =========================================================================

	@Nested
	@DisplayName("Read isolation")
	class Read {

		@Test
		@DisplayName("admin A only sees A's teachers")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(TEACHERS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode page = objectMapper.readTree(response.getBody());

			List<Teacher> bTeachers = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> teacherRepository.findAll()));
			List<UUID> bIds = bTeachers.stream().map(Teacher::getPublicUuid).toList();

			JsonNode content = page.get("content");
			assertThat(content.size()).isGreaterThan(0);
			for (JsonNode item : content) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B teacher publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's teacher by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fixture = setupTenants();

			Teacher anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> teacherRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					TEACHERS_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// Write isolation
	// =========================================================================

	@Nested
	@DisplayName("Write isolation")
	class Write {

		@Test
		@DisplayName("Same documentNumber works in two different tenants")
		void duplicateDocumentIsPerTenant() throws Exception {
			Fixture fixture = setupTenants();
			// Both tenants seeded a teacher with documentNumber "55555555".
			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> teacherRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> teacherRepository.count()));
			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fixture = setupTenants();

			Teacher anyOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> teacherRepository.findAll().get(0)));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					TEACHERS_BASE + "/" + anyOfB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant link-user reference → 404")
		void crossTenantLinkUserIs404() throws Exception {
			Fixture fixture = setupTenants();

			// Pick a TEACHER user inside tenant B.
			User userOfB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> {
						User u = new User();
						u.setEmail("teach-b-" + UUID.randomUUID() + "@x.test");
						u.setPasswordHash(passwordEncoder.encode("StrongPass-1!"));
						u.setFirstName("T");
						u.setLastName("B");
						u.setStatus(UserStatus.ACTIVE);
						u.setEmailVerified(true);
						u.setMfaEnabled(false);
						u.addRole(UserRole.TEACHER);
						return userRepository.saveAndFlush(u);
					}));

			Teacher teacherOfA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> teacherRepository.findAll().stream()
							.filter(t -> t.getUserId() == null).findFirst().orElseThrow()));

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"userPublicUuid\":\"" + userOfB.getPublicUuid() + "\"}";

			ResponseEntity<String> response = doPost(
					TEACHERS_BASE + "/" + teacherOfA.getPublicUuid() + "/link-user",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// HTTP helpers
	// =========================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private AuthResponse login(String slug, String email, String password) throws Exception {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", slug);
		String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		ResponseEntity<String> response = rest.exchange(AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
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

	private ResponseEntity<String> doPost(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doDelete(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	record Fixture(Tenant tenantA, Tenant tenantB) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-teach-a-");
		Tenant tenantB = createTenant("it-teach-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);

		// Each tenant gets a teacher with the same documentNumber on purpose
		// — the partial unique index is per-tenant, both should coexist.
		seedTeacher(tenantA, "55555555", "ada-a@acme.test");
		seedTeacher(tenantB, "55555555", "ada-b@acme.test");

		return new Fixture(tenantA, tenantB);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private void createAdmin(Tenant tenant, String email, String rawPassword) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash(passwordEncoder.encode(rawPassword));
					user.setFirstName("It");
					user.setLastName(tenant.getSlug());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.TENANT_ADMIN);
					return userRepository.saveAndFlush(user);
				}));
	}

	private void seedTeacher(Tenant tenant, String docNumber, String email) {
		TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Teacher t = new Teacher();
			t.setDocumentType(DocumentType.DNI);
			t.setDocumentNumber(docNumber);
			t.setFirstName("Ada");
			t.setLastName("Lovelace");
			t.setEmail(email);
			t.setEmploymentStatus(EmploymentStatus.ACTIVE);
			return teacherRepository.saveAndFlush(t);
		}));
	}
}
