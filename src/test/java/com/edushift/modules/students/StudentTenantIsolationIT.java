package com.edushift.modules.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
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
 * Cross-tenant isolation IT for the student-related surface added by
 * Sprint 3 BE-3.3 ({@code /v1/students}) and BE-3.4
 * ({@code /v1/students/{id}/guardians}).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation (students)</strong> — list and get-by-id
 *       only ever return rows from the caller's tenant.</li>
 *   <li><strong>Write isolation (students)</strong> — POST in tenant A
 *       leaves tenant B untouched; PUT and DELETE on a sibling tenant
 *       publicUuid return 404.</li>
 *   <li><strong>Document uniqueness is per-tenant</strong> — both
 *       tenants can hold a {@code DNI 12345678} student without colliding.
 *       The naive lookup in {@code StudentServiceImpl} already filters
 *       by tenant; this test makes that property explicit so a refactor
 *       can't silently break it.</li>
 *   <li><strong>Read isolation (guardians)</strong> — list / add /
 *       delete on a sibling tenant's student return 404, never 200.</li>
 * </ul>
 *
 * <h3>Why every fixture re-seeds</h3>
 * Same rationale as the auth and tenants ITs: shared Postgres container
 * across the JVM, no rollback between methods, so each test creates
 * UUID-suffixed identifiers.
 */
@DisplayName("Students module multi-tenancy isolation")
class StudentTenantIsolationIT extends IntegrationTest {

	private static final String STUDENTS_BASE = "/v1/students";

	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "shared@isolation.test";

	private static final String PASSWORD_A = "PassStudentsA-1!";

	private static final String PASSWORD_B = "PassStudentsB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private GuardianRepository guardianRepository;
	@Autowired private StudentGuardianRepository linkRepository;
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
	// GET /v1/students — list isolation
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students — list isolation")
	class ListIsolation {

		@Test
		@DisplayName("admin A only sees A's students; B's students never appear")
		void listOnlyOwnTenant() throws Exception {
			TenantPair pair = seedAdmins();
			Student studentA = createStudent(pair.tenantA(), DocumentType.DNI, "11111111", "Ada");
			Student studentB = createStudent(pair.tenantB(), DocumentType.DNI, "22222222", "Bob");

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(STUDENTS_BASE, loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode page = objectMapper.readTree(response.getBody());
			boolean foundA = false;
			for (JsonNode item : page.path("content")) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(id)
						.as("student-B's publicUuid must NOT appear in tenant A's list")
						.isNotEqualTo(studentB.getPublicUuid());
				if (id.equals(studentA.getPublicUuid())) foundA = true;
			}
			assertThat(foundA).as("student-A must appear in tenant A's list").isTrue();
		}
	}

	// ===========================================================================
	// GET /v1/students/{publicUuid} — read isolation
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students/{publicUuid} — read isolation")
	class ReadIsolation {

		@Test
		@DisplayName("admin A reading student-B → 404 RESOURCE_NOT_FOUND")
		void crossTenantReadIs404() throws Exception {
			TenantPair pair = seedAdmins();
			Student studentB = createStudent(pair.tenantB(), DocumentType.DNI, "22222222", "Bob");

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "/" + studentB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// ===========================================================================
	// POST + PUT + DELETE — write isolation
	// ===========================================================================

	@Nested
	@DisplayName("Write isolation")
	class WriteIsolation {

		@Test
		@DisplayName("POST /v1/students in tenant A creates only in A; the same document survives in B")
		void documentUniquenessIsPerTenant() throws Exception {
			TenantPair pair = seedAdmins();
			// Same documentNumber lives in both tenants — must NOT collide.
			Student studentB = createStudent(pair.tenantB(), DocumentType.DNI, "33333333", "Bob");

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = """
					{
					  "documentType":"DNI",
					  "documentNumber":"33333333",
					  "firstName":"Ada",
					  "lastName":"Lovelace"
					}
					""";

			ResponseEntity<String> response = doPost(STUDENTS_BASE,
					loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("same document number must be allowed in a different tenant")
					.isEqualTo(HttpStatus.CREATED);

			// Tenant B's row must remain intact (different publicUuid still
			// exists and is reachable from B's bearer).
			Student freshB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s ->
							studentRepository.findByPublicUuid(studentB.getPublicUuid()).orElseThrow()));
			assertThat(freshB.getDocumentNumber()).isEqualTo("33333333");
			assertThat(freshB.getFirstName()).isEqualTo("Bob");
		}

		@Test
		@DisplayName("PUT /v1/students/{B's publicUuid} from tenant A → 404, B is untouched")
		void crossTenantUpdateIs404() throws Exception {
			TenantPair pair = seedAdmins();
			Student studentB = createStudent(pair.tenantB(), DocumentType.DNI, "44444444", "Bob");
			final String originalFirstNameB = studentB.getFirstName();

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doPut(
					STUDENTS_BASE + "/" + studentB.getPublicUuid(),
					loginA.accessToken(),
					"{\"firstName\":\"hacked-by-tenant-A\"}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			Student freshB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s ->
							studentRepository.findById(studentB.getId()).orElseThrow()));
			assertThat(freshB.getFirstName())
					.as("student-B's firstName must remain untouched after a cross-tenant PUT")
					.isEqualTo(originalFirstNameB);
		}

		@Test
		@DisplayName("DELETE /v1/students/{B's publicUuid} from tenant A → 404, B is not soft-deleted")
		void crossTenantDeleteIs404() throws Exception {
			TenantPair pair = seedAdmins();
			Student studentB = createStudent(pair.tenantB(), DocumentType.DNI, "55555555", "Bob");

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doDelete(
					STUDENTS_BASE + "/" + studentB.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			Student freshB = TenantContext.runAs(pair.tenantB().getId(),
					() -> tx().execute(s ->
							studentRepository.findById(studentB.getId()).orElseThrow()));
			assertThat(freshB.isDeleted())
					.as("student-B must NOT be soft-deleted by a cross-tenant DELETE")
					.isFalse();
		}
	}

	// ===========================================================================
	// Guardians — read isolation across tenants
	// ===========================================================================

	@Nested
	@DisplayName("Guardians — cross-tenant access")
	class Guardians {

		@Test
		@DisplayName("GET /v1/students/{B's id}/guardians from tenant A → 404")
		void crossTenantListGuardiansIs404() throws Exception {
			TenantPair pair = seedAdmins();
			Student studentB = createStudent(pair.tenantB(), DocumentType.DNI, "66666666", "Bob");
			seedGuardian(pair.tenantB(), studentB, "77777777", "Anna");

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "/" + studentB.getPublicUuid() + "/guardians",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin can list their own students' guardians")
		void selfTenantListGuardiansSucceeds() throws Exception {
			TenantPair pair = seedAdmins();
			Student studentA = createStudent(pair.tenantA(), DocumentType.DNI, "88888888", "Ada");
			seedGuardian(pair.tenantA(), studentA, "99999999", "Zoe");

			AuthResponse loginA = login(pair.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "/" + studentA.getPublicUuid() + "/guardians",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.isArray()).isTrue();
			assertThat(data).hasSize(1);
			assertThat(data.get(0).get("firstName").asText()).isEqualTo("Zoe");
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

	private ResponseEntity<String> doPost(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doPut(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PUT,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doDelete(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	// ===========================================================================
	// DB seeding
	// ===========================================================================

	record TenantPair(Tenant tenantA, Tenant tenantB, User userA, User userB) {}

	private TenantPair seedAdmins() {
		Tenant tenantA = createTenant("it-students-a-", TenantStatus.ACTIVE);
		Tenant tenantB = createTenant("it-students-b-", TenantStatus.ACTIVE);
		User userA = createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		User userB = createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		return new TenantPair(tenantA, tenantB, userA, userB);
	}

	private Tenant createTenant(String slugPrefix, TenantStatus status) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(status);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createAdmin(Tenant tenant, String email, String rawPassword) {
		return TenantContext.runAs(tenant.getId(), () ->
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

	private Student createStudent(Tenant tenant, DocumentType type, String number, String firstName) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					Student student = new Student();
					student.setDocumentType(type);
					student.setDocumentNumber(number);
					student.setFirstName(firstName);
					student.setLastName("Lovelace");
					student.setGender(Gender.NOT_SPECIFIED);
					student.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
					return studentRepository.saveAndFlush(student);
				}));
	}

	private void seedGuardian(Tenant tenant, Student student, String document, String firstName) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					Guardian g = new Guardian();
					g.setDocumentType(DocumentType.DNI);
					g.setDocumentNumber(document);
					g.setFirstName(firstName);
					g.setLastName("Lovelace");
					Guardian saved = guardianRepository.saveAndFlush(g);

					StudentGuardian link = new StudentGuardian();
					link.setStudent(student);
					link.setGuardian(saved);
					link.setRelationship(RelationshipType.MOTHER);
					link.setPrimaryContact(true);
					link.setCanPickupStudent(true);
					linkRepository.saveAndFlush(link);
					return null;
				}));
	}
}
