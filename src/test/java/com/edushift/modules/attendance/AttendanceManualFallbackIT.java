package com.edushift.modules.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Integration tests for the Sprint 6 / BE-6.8 manual fallback:
 * <ul>
 *   <li>{@code POST /v1/attendance/manual-check-in}</li>
 *   <li>{@code GET  /v1/attendance/students/lookup}</li>
 * </ul>
 *
 * <h3>Why a dedicated IT</h3>
 * The QR-driven check-in is already covered by
 * {@link AttendanceTenantIsolationIT}. The manual path adds:
 * <ol>
 *   <li>Auto-resolution of the target session from the student's
 *       current ACTIVE enrollment (creates a session if none exists).</li>
 *   <li>A new error code {@code STUDENT_NO_ACTIVE_ENROLLMENT}
 *       (422) when the picked student is not currently enrolled.</li>
 *   <li>A TEACHER-accessible lookup endpoint with three optional
 *       cascading filters (Level → Grade → Section).</li>
 * </ol>
 *
 * <p>The cross-tenant guarantees come "for free" from the existing
 * {@code @TenantId} discriminator on {@code Student},
 * {@code StudentEnrollment}, {@code Section}, etc. — but we still
 * assert the wire contract because the SecurityConfig / controller
 * layer is the public-facing surface.
 */
@DisplayName("Attendance manual fallback (BE-6.8)")
class AttendanceManualFallbackIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";
	private static final String MANUAL_BASE = "/v1/attendance/manual-check-in";
	private static final String LOOKUP_BASE = "/v1/attendance/students/lookup";

	private static final String SHARED_EMAIL = "manual-fb@isolation.test";
	private static final String PASSWORD_A = "PassFbA-1!";
	private static final String PASSWORD_B = "PassFbB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private AttendanceSessionRepository sessionRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx() {
		return new TransactionTemplate(txManager);
	}

	// =====================================================================
	// Manual check-in
	// =====================================================================

	@Nested
	@DisplayName("POST /attendance/manual-check-in")
	class ManualCheckIn {

		@Test
		@DisplayName("happy path: auto-opens a session and creates a record (201)")
		void happyPath() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			String body = "{\"studentPublicUuid\":\""
					+ fx.studentA().getPublicUuid() + "\"}";

			ResponseEntity<String> response = doPost(
					MANUAL_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.CREATED);

			JsonNode data = objectMapper.readTree(response.getBody())
					.path("data");
			assertThat(data.path("studentPublicUuid").asText())
					.isEqualTo(fx.studentA().getPublicUuid().toString());
			assertThat(data.path("status").asText())
					.isIn("PRESENT", "LATE");
			assertThat(data.path("wasIdempotent").asBoolean()).isFalse();
		}

		@Test
		@DisplayName("idempotent: second call returns the same record (200)")
		void idempotent() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			String body = "{\"studentPublicUuid\":\""
					+ fx.studentA().getPublicUuid() + "\"}";

			ResponseEntity<String> first = doPost(MANUAL_BASE,
					loginA.accessToken(), body);
			assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			String firstRecord = objectMapper.readTree(first.getBody())
					.path("data").path("publicUuid").asText();

			ResponseEntity<String> second = doPost(MANUAL_BASE,
					loginA.accessToken(), body);

			assertThat(second.getStatusCode())
					.as("body=%s", second.getBody())
					.isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(second.getBody())
					.path("data");
			assertThat(data.path("publicUuid").asText()).isEqualTo(firstRecord);
			assertThat(data.path("wasIdempotent").asBoolean()).isTrue();
		}

		@Test
		@DisplayName("cross-tenant student → 404 RESOURCE_NOT_FOUND")
		void crossTenantStudentIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			String body = "{\"studentPublicUuid\":\""
					+ fx.studentB().getPublicUuid() + "\"}";

			ResponseEntity<String> response = doPost(
					MANUAL_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("student with no ACTIVE enrollment → 422 STUDENT_NO_ACTIVE_ENROLLMENT")
		void noActiveEnrollmentIs422() throws Exception {
			Fixture fx = setupTenants();

			// Withdraw the only enrollment to leave the student "active"
			// at the institution but without a current section.
			TenantContext.runAs(fx.tenantA().getId(), () ->
					tx().execute(s -> {
						List<StudentEnrollment> active = enrollmentRepository
								.findActiveByStudent(fx.studentA());
						for (StudentEnrollment e : active) {
							e.setStatus(StudentEnrollmentStatus.WITHDRAWN);
							e.setWithdrawnAt(LocalDate.now());
							enrollmentRepository.saveAndFlush(e);
						}
						return null;
					}));

			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			String body = "{\"studentPublicUuid\":\""
					+ fx.studentA().getPublicUuid() + "\"}";

			ResponseEntity<String> response = doPost(
					MANUAL_BASE, loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
			assertThat(response.getBody())
					.contains("STUDENT_NO_ACTIVE_ENROLLMENT");
		}
	}

	// =====================================================================
	// Student lookup
	// =====================================================================

	@Nested
	@DisplayName("GET /attendance/students/lookup")
	class Lookup {

		@Test
		@DisplayName("no filter: returns only the bearer-tenant's students")
		void lookupReturnsOnlyOwnTenant() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(LOOKUP_BASE,
					loginA.accessToken(), java.util.Map.of());

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.OK);
			String body = response.getBody();
			assertThat(body)
					.as("A's listing must contain A's student")
					.contains(fx.studentA().getPublicUuid().toString());
			assertThat(body)
					.as("A's listing must NOT contain B's student")
					.doesNotContain(fx.studentB().getPublicUuid().toString());
		}

		@Test
		@DisplayName("?q=lastName matches the student case-insensitively")
		void lookupByName() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(LOOKUP_BASE,
					loginA.accessToken(),
					java.util.Map.of("q", "PER"));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody())
					.contains(fx.studentA().getPublicUuid().toString());
		}

		@Test
		@DisplayName("?sectionPublicUuid filter narrows to that section's students")
		void lookupBySection() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(LOOKUP_BASE,
					loginA.accessToken(),
					java.util.Map.of("sectionPublicUuid",
							fx.sectionA().getPublicUuid().toString()));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody())
					.contains(fx.studentA().getPublicUuid().toString())
					.doesNotContain(fx.studentB().getPublicUuid().toString());
		}

		@Test
		@DisplayName("?sectionPublicUuid pointing to a foreign-tenant section returns empty")
		void lookupBySectionForeignTenantIsEmpty() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(LOOKUP_BASE,
					loginA.accessToken(),
					java.util.Map.of("sectionPublicUuid",
							fx.sectionB().getPublicUuid().toString()));

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			// B's section is invisible to A → no students match.
			assertThat(response.getBody())
					.doesNotContain(fx.studentB().getPublicUuid().toString());
		}
	}

	// =====================================================================
	// HTTP helpers
	// =====================================================================

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private AuthResponse login(String slug, String email, String password)
			throws Exception {
		HttpHeaders headers = jsonHeaders();
		headers.add("X-Tenant-Slug", slug);
		String body = String.format(
				"{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
		ResponseEntity<String> response = rest.exchange(
				AUTH_BASE + "/login", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
		assertThat(response.getStatusCode())
				.as("seed login() requires HTTP 200; body=%s",
						response.getBody())
				.isEqualTo(HttpStatus.OK);
		return objectMapper.readValue(
				response.getBody(), AuthResponse.class);
	}

	private ResponseEntity<String> doPost(String path, String bearer,
			String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doGet(String path, String bearer,
			java.util.Map<String, ?> queryParams) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		UriComponentsBuilder uri = UriComponentsBuilder.fromPath(path);
		for (java.util.Map.Entry<String, ?> e : queryParams.entrySet()) {
			if (e.getValue() != null) {
				uri.queryParam(e.getKey(), e.getValue().toString());
			}
		}
		return rest.exchange(uri.toUriString(), HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
	}

	// =====================================================================
	// Fixture
	// =====================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			Section sectionA, Section sectionB,
			Student studentA, Student studentB
	) {
	}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-fb-a-");
		Tenant tenantB = createTenant("it-fb-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		Bundle bundleA = seedBundle(tenantA);
		Bundle bundleB = seedBundle(tenantB);

		return new Fixture(tenantA, tenantB,
				bundleA.section(), bundleB.section(),
				bundleA.student(), bundleB.student());
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
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

	private void seedAcademicCatalog(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					seedService.seedDefaults(tenant.getId());
					return null;
				}));
	}

	record Bundle(Section section, Student student) {
	}

	private Bundle seedBundle(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository
					.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			Grade grade = gradeRepository
					.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

			AcademicYear year = new AcademicYear();
			year.setName("2026-IT-FB-" + tenant.getSlug().substring(0, 4));
			year.setStartDate(LocalDate.of(2026, 3, 1));
			year.setEndDate(LocalDate.of(2026, 12, 20));
			year.setStatus(AcademicYearStatus.ACTIVE);
			AcademicYear savedYear = yearRepository.saveAndFlush(year);

			Section section = new Section();
			section.setAcademicYear(savedYear);
			section.setGrade(grade);
			section.setName("1ro A");
			Section savedSection = sectionRepository.saveAndFlush(section);

			Student student = new Student();
			student.setDocumentType(DocumentType.DNI);
			student.setDocumentNumber("12345678"
					+ tenant.getSlug().substring(0, 1).toUpperCase());
			student.setFirstName("Juan");
			student.setLastName("Perez");
			Student savedStudent = studentRepository.saveAndFlush(student);

			StudentEnrollment enrollment = new StudentEnrollment();
			enrollment.setStudent(savedStudent);
			enrollment.setSection(savedSection);
			enrollment.setAcademicYear(savedYear);
			enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
			enrollment.setEnrolledAt(LocalDate.now());
			enrollmentRepository.saveAndFlush(enrollment);

			return new Bundle(savedSection, savedStudent);
		}));
	}
}
