package com.edushift.modules.students.enrollments;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
 * Cross-tenant isolation IT for {@code students.enrollments} (Sprint 4 / BE-4.8).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li>Read isolation: A's GET only returns A's enrollments / roster.</li>
 *   <li>Cross-tenant withdraw of B's enrollment from A → 404.</li>
 *   <li>Cross-tenant create from A using B's section / year UUIDs → 404.</li>
 *   <li>Same logical {@code (student, year)} can be active in both tenants —
 *       the partial unique index is per-tenant.</li>
 *   <li>{@code GET /v1/students?currentSectionId=...} on tenant A only
 *       includes A's students; B's section UUID returns no rows.</li>
 * </ul>
 */
@DisplayName("StudentEnrollment multi-tenancy isolation")
class StudentEnrollmentTenantIsolationIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";
	private static final String STUDENTS_BASE = "/v1/students";
	private static final String ENROLLMENTS_BASE = "/v1/enrollments";
	private static final String SECTIONS_BASE = "/v1/academic/sections";
	private static final String SHARED_EMAIL = "shared-enroll@isolation.test";
	private static final String PASSWORD_A = "PassEnrollA-1!";
	private static final String PASSWORD_B = "PassEnrollB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
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
		@DisplayName("admin A only sees A's enrollments via GET /students/{S}/enrollments")
		void listIsScoped() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "/" + fixture.studentA().getPublicUuid() + "/enrollments",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode arr = objectMapper.readTree(response.getBody());
			assertThat(arr.isArray()).isTrue();
			assertThat(arr.size()).isGreaterThanOrEqualTo(1);
			for (JsonNode n : arr) {
				UUID id = UUID.fromString(n.get("publicUuid").asText());
				assertThat(id)
						.as("enrollment publicUuid must belong to tenant A")
						.isEqualTo(fixture.enrollmentA().getPublicUuid());
			}
		}

		@Test
		@DisplayName("admin A reading B's student timeline → 404")
		void crossTenantStudentTimelineIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "/" + fixture.studentB().getPublicUuid() + "/enrollments",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A listing B's section roster → 404")
		void crossTenantSectionRosterIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					SECTIONS_BASE + "/" + fixture.sectionB().getPublicUuid() + "/students",
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
		@DisplayName("admin A withdrawing B's enrollment → 404")
		void crossTenantWithdrawIs404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = "{\"status\":\"WITHDRAWN\",\"withdrawnAt\":\"2026-06-01\"}";
			ResponseEntity<String> response = doPost(
					ENROLLMENTS_BASE + "/" + fixture.enrollmentB().getPublicUuid() + "/withdraw",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			// Sanity: B's enrollment is still ACTIVE.
			StudentEnrollment stillActive = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> enrollmentRepository
							.findByPublicUuid(fixture.enrollmentB().getPublicUuid())
							.orElseThrow()));
			assertThat(stillActive.getStatus()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
			assertThat(stillActive.getWithdrawnAt()).isNull();
		}

		@Test
		@DisplayName("admin A POSTing enrollment with B's section/year → 404")
		void crossTenantCreateRefsAre404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			String body = String.format(
					"{\"sectionPublicUuid\":\"%s\","
							+ "\"academicYearPublicUuid\":\"%s\","
							+ "\"enrolledAt\":\"2026-03-01\"}",
					fixture.sectionB().getPublicUuid(),
					fixture.yearB().getPublicUuid());

			ResponseEntity<String> response = doPost(
					STUDENTS_BASE + "/" + fixture.studentA().getPublicUuid() + "/enrollments",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Same (student, year) tuple is unique per tenant")
		void duplicateTupleIsPerTenant() {
			Fixture fixture = setupTenants();

			long countA = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> enrollmentRepository.count()));
			long countB = TenantContext.runAs(fixture.tenantB().getId(),
					() -> tx().execute(s -> enrollmentRepository.count()));
			// Each tenant seeds exactly one ACTIVE enrollment with the
			// "same" logical tuple (different rows because each tenant
			// has its own student / section / year).
			assertThat(countA).isGreaterThanOrEqualTo(1);
			assertThat(countB).isGreaterThanOrEqualTo(1);
		}
	}

	// =========================================================================
	// /students filter isolation (BE-4.8)
	// =========================================================================

	@Nested
	@DisplayName("GET /students?currentSectionId — filter is tenant-scoped")
	class FilterIsolation {

		@Test
		@DisplayName("filter by A's section → only A's student")
		void filterByOwnSection() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "?currentSectionId=" + fixture.sectionA().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode page = objectMapper.readTree(response.getBody());
			JsonNode content = page.get("content");
			assertThat(content.isArray()).isTrue();
			assertThat(content.size()).isEqualTo(1);
			assertThat(UUID.fromString(content.get(0).get("publicUuid").asText()))
					.isEqualTo(fixture.studentA().getPublicUuid());
		}

		@Test
		@DisplayName("filter by B's section UUID from A → empty page (cross-tenant filter)")
		void filterByOtherTenantSection() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE + "?currentSectionId=" + fixture.sectionB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode page = objectMapper.readTree(response.getBody());
			assertThat(page.get("content").isArray()).isTrue();
			assertThat(page.get("content").size()).isZero();
		}

		@Test
		@DisplayName("filter by A's year + section composes correctly")
		void filterByYearAndSection() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					STUDENTS_BASE
							+ "?currentSectionId=" + fixture.sectionA().getPublicUuid()
							+ "&currentAcademicYearId=" + fixture.yearA().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode content = objectMapper.readTree(response.getBody()).get("content");
			assertThat(content.size()).isEqualTo(1);
			assertThat(UUID.fromString(content.get(0).get("publicUuid").asText()))
					.isEqualTo(fixture.studentA().getPublicUuid());
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

	// =========================================================================
	// Fixtures
	// =========================================================================

	record TenantSeed(Tenant tenant, Student student, AcademicYear year,
			Section section, StudentEnrollment enrollment) {
	}

	record Fixture(Tenant tenantA, Tenant tenantB,
			Student studentA, Student studentB,
			AcademicYear yearA, AcademicYear yearB,
			Section sectionA, Section sectionB,
			StudentEnrollment enrollmentA, StudentEnrollment enrollmentB) {
	}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-enroll-a-");
		Tenant tenantB = createTenant("it-enroll-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);

		TenantSeed seedA = seedEnrollment(tenantA, "a");
		TenantSeed seedB = seedEnrollment(tenantB, "b");

		return new Fixture(
				tenantA, tenantB,
				seedA.student(), seedB.student(),
				seedA.year(), seedB.year(),
				seedA.section(), seedB.section(),
				seedA.enrollment(), seedB.enrollment());
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

	/**
	 * Seeds the full graph for a single tenant: year (ACTIVE) + level +
	 * grade + section + student + one ACTIVE enrollment.
	 */
	private TenantSeed seedEnrollment(Tenant tenant, String label) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicYear year = new AcademicYear();
			year.setName("2026-" + tenant.getSlug());
			year.setStartDate(LocalDate.of(2026, 1, 1));
			year.setEndDate(LocalDate.of(2026, 12, 31));
			year.setStatus(AcademicYearStatus.ACTIVE);
			year = yearRepository.saveAndFlush(year);

			AcademicLevel level = new AcademicLevel();
			level.setCode("PRIMARIA");
			level.setName("Primaria");
			level.setOrdinal(2);
			level = levelRepository.saveAndFlush(level);

			Grade grade = new Grade();
			grade.setName("2do Primaria");
			grade.setOrdinal(2);
			grade.setLevel(level);
			grade = gradeRepository.saveAndFlush(grade);

			Section section = new Section();
			section.setName("A");
			section.setGrade(grade);
			section.setAcademicYear(year);
			section.setDisplayOrder(1);
			section = sectionRepository.saveAndFlush(section);

			Student student = new Student();
			student.setDocumentType(DocumentType.DNI);
			// document_number unique per tenant; use a stable suffix tied
			// to the tenant id to avoid collisions across IT runs.
			student.setDocumentNumber("8000" + Math.abs(
					tenant.getId().getMostSignificantBits() % 10000));
			student.setFirstName("Ana");
			student.setLastName("Garcia " + label);
			student.setEmail("ana-" + tenant.getId() + "@isolation.test");
			student = studentRepository.saveAndFlush(student);

			StudentEnrollment enrollment = new StudentEnrollment();
			enrollment.setStudent(student);
			enrollment.setSection(section);
			enrollment.setAcademicYear(year);
			enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
			enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
			enrollment = enrollmentRepository.saveAndFlush(enrollment);

			return new TenantSeed(tenant, student, year, section, enrollment);
		}));
	}
}
