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
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Cross-tenant isolation IT for the {@code attendance} module
 * (Sprint 6 / BE-6.6).
 *
 * <h3>What is tested (D-ATT §7.2 / D-ATT §9.3)</h3>
 * <ul>
 *   <li><strong>XT-ATT-1</strong> — Bearer Tenant A → {@code GET
 *       /sessions/{B-uuid}/records} → 404 (records invisible across
 *       tenants).</li>
 *   <li><strong>XT-ATT-2</strong> — Bearer Tenant A →
 *       {@code POST /sessions/{B-uuid}/check-in} → 404.</li>
 *   <li><strong>XT-ATT-3</strong> — Bearer Tenant A → check-in with a
 *       JWT minted for Tenant B (same secret, correct
 *       {@code typ="attendance"}, but a different {@code tenant_id}
 *       claim) → 404. Internally the service stamps
 *       {@code QR_TENANT_MISMATCH} in the audit log; the wire returns
 *       a generic 404 to avoid leaking existence.</li>
 *   <li><strong>XT-ATT-4</strong> — Bearer Tenant A →
 *       {@code POST /students/{B-uuid}/attendance-qr/rotate} → 404.</li>
 *   <li><strong>XT-ATT-5</strong> — Bearer Tenant A →
 *       {@code PUT /records/{B-uuid}} → 404.</li>
 *   <li><strong>XT-ATT-6</strong> — Same student publicUuid in two
 *       tenants does not collide (UUIDv4 is tenant-agnostic by
 *       construction, so the {@code @TenantId} discriminator is the
 *       only protection; we assert that the same UUID across tenants
 *       resolves to two distinct rows).</li>
 *   <li><strong>Happy</strong> — Bearer Tenant A can read + write
 *       their own session's records; sanity check that
 *       "200 ≠ 404" works as expected.</li>
 * </ul>
 *
 * <h3>QR minting</h3>
 * Scenarios that need a "valid JWT for tenant B" build it via the
 * production {@link QrTokenService} (HS256, same secret, different
 * {@code tenant_id} claim). This is the same code path the real
 * issuance endpoint uses, so the test exercises the real validation
 * logic end-to-end.
 *
 * <p>Like its siblings ({@code GradeRecordTenantIsolationIT},
 * {@code EvaluationTenantIsolationIT}), this IT requires Docker
 * because {@link IntegrationTest} bootstraps a real Postgres
 * container. Compiles offline; running needs Docker Desktop up.
 */
@DisplayName("Attendance multi-tenancy isolation")
class AttendanceTenantIsolationIT extends IntegrationTest {

	private static final String AUTH_BASE = "/v1/auth";
	private static final String SESSIONS_BASE = "/v1/attendance/sessions";
	private static final String RECORDS_BASE = "/v1/attendance/records";
	private static final String QR_BASE_PREFIX = "/v1/students";
	private static final String QR_ROTATE_SUFFIX = "/attendance-qr/rotate";
	private static final String QR_INFO_SUFFIX = "/attendance-qr/info";

	private static final String SHARED_EMAIL = "shared-att@isolation.test";
	private static final String PASSWORD_A = "PassAttA-1!";
	private static final String PASSWORD_B = "PassAttB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private AttendanceSessionRepository sessionRepository;
	@Autowired private AttendanceRecordRepository recordRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private QrTokenService qrTokenService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx() {
		return new TransactionTemplate(txManager);
	}

	// =====================================================================
	// Cross-tenant access — the six XT-ATT scenarios
	// =====================================================================

	@Nested
	@DisplayName("Cross-tenant access returns 404 (anti-enumeration)")
	class CrossTenant {

		@Test
		@DisplayName("XT-ATT-1: GET /sessions/{B-uuid}/records from A → 404")
		void listRecordsAcrossTenantsIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					SESSIONS_BASE + "/" + fx.sessionB().getPublicUuid()
							+ "/records",
					loginA.accessToken());

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("XT-ATT-2: POST /sessions/{B-uuid}/check-in from A → 404")
		void checkInAcrossTenantsIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			// We don't even need a valid JWT here: the session lookup
			// happens before token parsing, and the
			// TenantAwareEntity filter for sessions already returns
			// empty. Asserts 404 regardless of the JWT body.
			String body = "{"
					+ "\"qrToken\":\"whatever\","
					+ "\"sessionPublicUuid\":\"" + fx.sessionB().getPublicUuid() + "\""
					+ "}";

			ResponseEntity<String> response = doPost(
					SESSIONS_BASE + "/" + fx.sessionB().getPublicUuid()
							+ "/check-in",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("XT-ATT-3: check-in with a JWT minted for tenant B from A → 404")
		void checkInWithCrossTenantQrIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			// Build a real HS256 JWT for tenant B's student
			// (sub=studentB.publicUuid, tenant_id=B.id,
			// typ="attendance"). The signature is valid; the only
			// "wrong" thing is the tenant claim. The service must
			// detect that BEFORE touching the DB and return a
			// generic 404 (anti-enumeration).
			String jwtForB = qrTokenService.issue(
					fx.studentB().getPublicUuid(),
					fx.tenantB().getId()).token();

			String body = "{"
					+ "\"qrToken\":\"" + jwtForB + "\","
					+ "\"sessionPublicUuid\":\"" + fx.sessionA().getPublicUuid() + "\""
					+ "}";

			ResponseEntity<String> response = doPost(
					SESSIONS_BASE + "/" + fx.sessionA().getPublicUuid()
							+ "/check-in",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("XT-ATT-4: POST /students/{B-uuid}/attendance-qr/rotate from A → 404")
		void rotateQrAcrossTenantsIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doPost(
					QR_BASE_PREFIX + "/" + fx.studentB().getPublicUuid()
							+ QR_ROTATE_SUFFIX,
					loginA.accessToken(), "{}");

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("XT-ATT-5: PUT /records/{B-uuid} from A → 404")
		void updateRecordAcrossTenantsIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			String body = "{\"status\":\"EXCUSED\",\"notes\":\"hijack\"}";
			ResponseEntity<String> response = rest.exchange(
					RECORDS_BASE + "/" + fx.recordB().getPublicUuid(),
					HttpMethod.PUT,
					new HttpEntity<>(body, jsonHeadersWithAuth(loginA.accessToken())),
					String.class);

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =====================================================================
	// Same publicUuid across tenants does NOT collide
	// =====================================================================

	@Nested
	@DisplayName("UUIDv4 isolation")
	class UuidIsolation {

		@Test
		@DisplayName("XT-ATT-6: same student.publicUuid across two tenants resolves to two distinct rows")
		void sharedPublicUuidDoesNotCollide() throws Exception {
			Fixture fx = setupTenants();
			// We force the same publicUuid in both tenants to
			// demonstrate that the @TenantId discriminator is the
			// only thing protecting against cross-tenant
			// collisions. UUIDv4 collisions are astronomically
			// rare in production but the test exercises the
			// "explicit collision" case directly.
			UUID sharedUuid = UUID.randomUUID();
			TenantContext.runAs(fx.tenantA().getId(), () ->
					tx().execute(s -> {
						fx.studentA().setPublicUuid(sharedUuid);
						studentRepository.saveAndFlush(fx.studentA());
						return null;
					}));
			TenantContext.runAs(fx.tenantB().getId(), () ->
					tx().execute(s -> {
						fx.studentB().setPublicUuid(sharedUuid);
						studentRepository.saveAndFlush(fx.studentB());
						return null;
					}));

			// Same shared publicUuid in both tenants:
			assertThat(fx.studentA().getPublicUuid())
					.isEqualTo(sharedUuid);
			assertThat(fx.studentB().getPublicUuid())
					.isEqualTo(sharedUuid);

			// But under tenant A's context only A's student
			// is visible.
			UUID foundInA = TenantContext.runAs(fx.tenantA().getId(), () ->
					tx().execute(s ->
							studentRepository.findByPublicUuid(sharedUuid)
									.orElseThrow().getId()));
			UUID foundInB = TenantContext.runAs(fx.tenantB().getId(), () ->
					tx().execute(s ->
							studentRepository.findByPublicUuid(sharedUuid)
									.orElseThrow().getId()));

			assertThat(foundInA)
					.isEqualTo(fx.studentA().getId());
			assertThat(foundInB)
					.isEqualTo(fx.studentB().getId());
			assertThat(foundInA)
					.as("internal ids must differ even when publicUuids match")
					.isNotEqualTo(foundInB);
		}
	}

	// =====================================================================
	// BE-6.7 — listing endpoint is tenant-scoped
	// =====================================================================

	@Nested
	@DisplayName("Listing endpoint never leaks across tenants")
	class ListingIsolation {

		@Test
		@DisplayName("XT-ATT-7: GET /sessions from A returns only A's sessions")
		void listSessionsAsTenantAExcludesB() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					SESSIONS_BASE,
					loginA.accessToken(),
					java.util.Map.of());

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.OK);
			// The body is a JSON envelope with a page of items; we
			// verify that B's session UUID never appears. Using
			// string-contains keeps the test resilient to changes in
			// the page shape.
			String body = response.getBody();
			assertThat(body)
					.as("A's listing must not contain B's session publicUuid")
					.doesNotContain(fx.sessionB().getPublicUuid().toString());
		}

		@Test
		@DisplayName("XT-ATT-8: filter by section from A only matches A's sections")
		void listSessionsWithSectionFilterIsTenantScoped() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			// Filter by A's section — should return A's session.
			ResponseEntity<String> response = doGet(
					SESSIONS_BASE,
					loginA.accessToken(),
					java.util.Map.of(
							"sectionPublicUuid",
							fx.sectionA().getPublicUuid().toString()));

			assertThat(response.getStatusCode())
					.as("body=%s", response.getBody())
					.isEqualTo(HttpStatus.OK);
			String body = response.getBody();
			assertThat(body)
					.as("A's filtered listing must not contain B's session")
					.doesNotContain(fx.sessionB().getPublicUuid().toString());
		}
	}

	// =====================================================================
	// Sanity: same-tenant reads + writes still work
	// =====================================================================

	@Nested
	@DisplayName("Same-tenant access returns 200")
	class SameTenant {

		@Test
		@DisplayName("Bearer A can read A's session records and edit them")
		void sameTenantEditRoundTrip() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(),
					SHARED_EMAIL, PASSWORD_A);

			// Read: 200 with a non-empty roster.
			ResponseEntity<String> list = doGet(
					SESSIONS_BASE + "/" + fx.sessionA().getPublicUuid()
							+ "/records",
					loginA.accessToken());
			assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(list.getBody());
			assertThat(body.isArray()).isTrue();
			assertThat(body.size()).isGreaterThan(0);

			// Edit: 200 with the updated status.
			String updateBody = "{\"status\":\"LATE\",\"notes\":\"IT patch\"}";
			ResponseEntity<String> put = rest.exchange(
					RECORDS_BASE + "/" + fx.recordA().getPublicUuid(),
					HttpMethod.PUT,
					new HttpEntity<>(updateBody,
							jsonHeadersWithAuth(loginA.accessToken())),
					String.class);
			assertThat(put.getStatusCode())
					.as("body=%s", put.getBody())
					.isEqualTo(HttpStatus.OK);
			JsonNode updated = objectMapper.readTree(put.getBody())
					.path("data");
			assertThat(updated.path("status").asText()).isEqualTo("LATE");
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

	private HttpHeaders jsonHeadersWithAuth(String bearer) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
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

	private ResponseEntity<String> doGet(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
	}

	/**
	 * Same as {@link #doGet(String, String)} but appends the given
	 * query params. Used by the BE-6.7 listing tests where the
	 * controller surfaces the page through `?from=…&to=…&sectionPublicUuid=…`.
	 */
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

	private ResponseEntity<String> doPost(String path, String bearer,
			String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	// =====================================================================
	// Fixture
	// =====================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			User adminA, User adminB,
			Section sectionA, Section sectionB,
			Student studentA, Student studentB,
			AttendanceSession sessionA, AttendanceSession sessionB,
			AttendanceRecord recordA, AttendanceRecord recordB
	) {
	}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-att-a-");
		Tenant tenantB = createTenant("it-att-b-");
		User adminA = createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		User adminB = createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		Bundle bundleA = seedAttendanceBundle(tenantA, adminA);
		Bundle bundleB = seedAttendanceBundle(tenantB, adminB);

		return new Fixture(tenantA, tenantB, adminA, adminB,
				bundleA.section(), bundleB.section(),
				bundleA.student(), bundleB.student(),
				bundleA.session(), bundleB.session(),
				bundleA.record(), bundleB.record());
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

	record Bundle(
			Section section,
			Student student,
			AttendanceSession session,
			AttendanceRecord record) {
	}

	private Bundle seedAttendanceBundle(Tenant tenant, User admin) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository
					.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			Grade grade = gradeRepository
					.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

			AcademicYear year = new AcademicYear();
			year.setName("2026-IT-ATT");
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
			student.setDocumentNumber("87654321"
					+ tenant.getSlug().substring(0, 1).toUpperCase());
			student.setFirstName("Ana");
			student.setLastName("Pérez");
			Student savedStudent = studentRepository.saveAndFlush(student);

			LocalDate today = LocalDate.now();
			AttendanceSession session = new AttendanceSession();
			session.setSection(savedSection);
			session.setOccurredOn(today);
			session.setSlot(AttendanceSessionSlot.MORNING);
			session.setStartsAt(Instant.now());
			session.setStatus(AttendanceSessionStatus.ACTIVE);
			AttendanceSession savedSession =
					sessionRepository.saveAndFlush(session);

			AttendanceRecord record = new AttendanceRecord();
			record.setSession(savedSession);
			record.setStudent(savedStudent);
			record.setStatus(AttendanceRecordStatus.PRESENT);
			record.setOccurredAt(Instant.now());
			record.setScannedByUserId(admin.getId());
			AttendanceRecord savedRecord =
					recordRepository.saveAndFlush(record);

			return new Bundle(savedSection, savedStudent,
					savedSession, savedRecord);
		}));
	}
}
