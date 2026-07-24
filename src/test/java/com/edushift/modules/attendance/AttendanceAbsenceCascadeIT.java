package com.edushift.modules.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 9A / BE-9A.1 — end-to-end test that closing an attendance session
 * with absent students emits one {@code STUDENT_ABSENT} notification per
 * linked guardian.
 *
 * <h3>Why this matters</h3>
 * Pre-Sprint 9A, the {@code AttendanceServiceImpl.materializeAbsentRecords}
 * publisher sent notifications to {@code student.userId}, which is null
 * for K-12 students (260/260 in the dev seed). The cascade was
 * therefore silent — no parent was ever notified. Sprint 9A fixes the
 * publisher to use {@code guardian.userId} via the
 * {@code StudentGuardianRepository} bulk lookup.
 *
 * <h3>What this test asserts</h3>
 * <ol>
 *   <li>3 enrolled students, 0 check-ins → close session materializes
 *       3 ABSENT records.</li>
 *   <li>Each student has a guardian link with {@code user_id} set.</li>
 *   <li>After close, {@code edushift.notifications} has 3 rows with
 *       {@code template_key='STUDENT_ABSENT'} and the recipient
 *       {@code user_id} is the <b>guardian's</b>, not the student's.</li>
 *   <li>Cross-tenant: closing a session in tenant A produces 0 rows in
 *       tenant B's notifications table.</li>
 * </ol>
 */
@DisplayName("Sprint 9A / BE-9A.1 -- STUDENT_ABSENT cascade end-to-end")
class AttendanceAbsenceCascadeIT extends IntegrationTest {

	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private com.edushift.modules.academic.section.repository.SectionRepository sectionRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private GuardianRepository guardianRepository;
	@Autowired private StudentGuardianRepository studentGuardianRepository;
	@Autowired private AttendanceSessionRepository sessionRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private TestRestTemplate rest;
	@Autowired private JdbcTemplate jdbcTemplate;

	private TransactionTemplate tx() {
		return new TransactionTemplate(txManager);
	}

	@Test
	@DisplayName(
			"SPRINT9A-CASCADE-1: close session with 3 ausentes -> 3 STUDENT_ABSENT notifications to PARENT user_ids")
	void closeSessionWithThreeAbsentees_threeParentNotificationsCreated() {
		Fixture fx = buildFixture("it-9a-a-", 3, /* withParentUserId */ true);

		// 1. Login as tenant admin
		String jwt = login(fx.tenant.getSlug(), "admin@it.test",
				"EduShift2026!");

		// 2. Open a session
		String openBody = "{\"sectionPublicUuid\":\"" + fx.section.getPublicUuid()
				+ "\",\"occurredOn\":\"" + LocalDate.now() + "\",\"slot\":\"MORNING\"}";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(jwt);
		headers.set("X-Tenant-Slug", fx.tenant.getSlug());

		ResponseEntity<String> openResp = rest.exchange(
				"/api/v1/attendance/sessions",
				HttpMethod.POST,
				new HttpEntity<>(openBody, headers),
				String.class);
		assertThat(openResp.getStatusCode())
				.as("open session body=%s", openResp.getBody())
				.isEqualTo(HttpStatus.CREATED);

		String sessionUuid = extractPublicUuid(openResp.getBody(), "data");
		assertThat(sessionUuid).as("session public uuid present").isNotBlank();

		// 3. Close the session -- no check-ins, so all 3 students materialize ABSENT.
		HttpHeaders closeHeaders = new HttpHeaders();
		closeHeaders.setBearerAuth(jwt);
		closeHeaders.set("X-Tenant-Slug", fx.tenant.getSlug());
		ResponseEntity<String> closeResp = rest.exchange(
				"/api/v1/attendance/sessions/" + sessionUuid + "/close",
				HttpMethod.PATCH,
				new HttpEntity<>(closeHeaders),
				String.class);
		assertThat(closeResp.getStatusCode())
				.as("close session body=%s", closeResp.getBody())
				.isEqualTo(HttpStatus.OK);

		// 4. Assert 3 notifications in edushift.notifications, recipients are PARENT user_ids
		Integer notifCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM edushift.notifications "
						+ "WHERE template_key = 'STUDENT_ABSENT' "
						+ "AND tenant_id = ?",
				Integer.class,
				fx.tenant.getId());
		assertThat(notifCount)
				.as("expected 3 STUDENT_ABSENT notifications in tenant %s", fx.tenant.getSlug())
				.isEqualTo(3);

		// 5. Verify recipients are GUARDIAN user_ids, NOT student user_ids
		List<UUID> recipients = jdbcTemplate.queryForList(
				"SELECT recipient_user_id FROM edushift.notifications "
						+ "WHERE template_key = 'STUDENT_ABSENT' "
						+ "AND tenant_id = ? ORDER BY recipient_user_id",
				UUID.class,
				fx.tenant.getId());
		assertThat(recipients).containsExactlyInAnyOrderElementsOf(fx.guardianUserIds);
		// And NEVER the student user_ids (which are null in this fixture).
		assertThat(recipients).doesNotContainNull();

		// 6. Sanity: every attendance_record was materialized ABSENT
		Integer absentRecords = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM edushift.attendance_records WHERE status = 'ABSENT' AND session_id IN ("
						+ "SELECT id FROM edushift.attendance_sessions WHERE public_uuid = ?::uuid)",
				Integer.class,
				sessionUuid);
		assertThat(absentRecords).isEqualTo(3);
	}

	@Test
	@DisplayName(
			"SPRINT9A-CASCADE-2: tenant B no ve las notifications de tenant A (cross-tenant)")
	void crossTenantIsolated_notificationsDoNotLeak() {
		Fixture fxA = buildFixture("it-9a-xa-", 2, true);
		Fixture fxB = buildFixture("it-9a-xb-", 2, true);

		// Close a session in tenant A.
		closeOneSessionFor(fxA);

		Integer countA = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM edushift.notifications "
						+ "WHERE template_key = 'STUDENT_ABSENT' AND tenant_id = ?",
				Integer.class, fxA.tenant.getId());
		Integer countB = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM edushift.notifications "
						+ "WHERE template_key = 'STUDENT_ABSENT' AND tenant_id = ?",
				Integer.class, fxB.tenant.getId());
		assertThat(countA).as("tenant A got its own notifications").isEqualTo(2);
		assertThat(countB).as("tenant B untouched by tenant A close").isZero();
	}

	@Test
	@DisplayName(
			"SPRINT9A-CASCADE-3: students con guardians SIN userId -> 0 notifications (skip con WARN)")
	void noGuardianUserId_zeroNotifications_noCrash() {
		Fixture fx = buildFixture("it-9a-z-", 2, /* withParentUserId */ false);
		closeOneSessionFor(fx);

		Integer count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM edushift.notifications "
						+ "WHERE template_key = 'STUDENT_ABSENT' AND tenant_id = ?",
				Integer.class, fx.tenant.getId());
		assertThat(count)
				.as("guardians without userId must not produce notifications")
				.isZero();

		// Sanity: ABSENT records still materialized (they are tracked even
		// when the parent can't be notified).
		Integer absentCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM edushift.attendance_records WHERE status = 'ABSENT' "
						+ "AND deleted = false AND session_id IN ("
						+ "SELECT id FROM edushift.attendance_sessions WHERE tenant_id = ?)",
				Integer.class, fx.tenant.getId());
		assertThat(absentCount).isEqualTo(2);
	}

	// =====================================================================
	// Fixture
	// =====================================================================

	private Fixture buildFixture(String slugPrefix, int studentCount,
			boolean withParentUserId) {
		Tenant tenant = createTenant(slugPrefix);
		createAdmin(tenant);

		// Seed default academic catalog (levels, grades).
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> { seedService.seedDefaults(tenant.getId()); return null; }));

		// Build the academic structure for the section.
		AcademicLevel level = levelRepository.findByCodeIgnoreCase("PRIMARIA")
				.orElseThrow();
		Grade grade = gradeRepository.findAllByLevelOrderByOrdinalAsc(level).get(0);

		AcademicYear year = TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicYear y = new AcademicYear();
			y.setName("AY-9A-" + tenant.getSlug().substring(0, 4));
			y.setStartDate(LocalDate.of(2026, 3, 1));
			y.setEndDate(LocalDate.of(2026, 12, 20));
			y.setStatus(AcademicYearStatus.ACTIVE);
			return yearRepository.saveAndFlush(y);
		}));

		com.edushift.modules.academic.section.entity.Section section =
				TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
					com.edushift.modules.academic.section.entity.Section sec =
							new com.edushift.modules.academic.section.entity.Section();
					sec.setAcademicYear(year);
					sec.setGrade(grade);
					sec.setName("1ro A");
					return sectionRepository.saveAndFlush(sec);
				}));

		// Students + Guardians + StudentGuardian links
		List<UUID> guardianUserIds = new ArrayList<>();
		for (int i = 0; i < studentCount; i++) {
			final int idx = i;
			TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
				Student student = new Student();
				student.setDocumentType(DocumentType.DNI);
				student.setDocumentNumber(String.format("%08d", 1000 + idx));
				student.setFirstName("Student" + idx);
				student.setLastName("Test");
				student.setUserId(null); // K-12: students don't have portal accounts.
				Student savedStudent = studentRepository.saveAndFlush(student);

				Guardian guardian = new Guardian();
				guardian.setDocumentType(DocumentType.DNI);
				guardian.setDocumentNumber(String.format("%08d", 2000 + idx));
				guardian.setFirstName("Madre" + idx);
				guardian.setLastName("F" + idx);
				guardian.setEmail("madre" + idx + "@it.test");
				UUID guardianUserId = withParentUserId
						? createLinkedUser(tenant.getSlug() + "-g" + idx,
								"Madre" + idx + " F" + idx,
								"madre" + idx + "@it.test").getPublicUuid()
						: null;
				if (guardianUserId != null) {
					guardian.setUserId(guardianUserId);
					guardianUserIds.add(guardianUserId);
				}
				Guardian savedGuardian = guardianRepository.saveAndFlush(guardian);

				StudentGuardian sg = new StudentGuardian();
				sg.setStudent(savedStudent);
				sg.setGuardian(savedGuardian);
				sg.setPrimaryContact(true);
				studentGuardianRepository.saveAndFlush(sg);

				StudentEnrollment enrollment = new StudentEnrollment();
				enrollment.setStudent(savedStudent);
				enrollment.setSection(section);
				enrollment.setAcademicYear(year);
				enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
				enrollment.setEnrolledAt(LocalDate.now());
				enrollmentRepository.saveAndFlush(enrollment);

				return null;
			}));
		}

		return new Fixture(tenant, section, guardianUserIds);
	}

private void closeOneSessionFor(Fixture fx) {
		String jwt = login(fx.tenant.getSlug(), "admin@it.test", "EduShift2026!");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(jwt);
		headers.set("X-Tenant-Slug", fx.tenant.getSlug());
		String openBody = "{\"sectionPublicUuid\":\"" + fx.section.getPublicUuid()
				+ "\",\"occurredOn\":\"" + LocalDate.now() + "\",\"slot\":\"MORNING\"}";
		ResponseEntity<String> openResp = rest.exchange(
				"/api/v1/attendance/sessions",
				HttpMethod.POST,
				new HttpEntity<>(openBody, headers),
				String.class);
		String sessionUuid = extractPublicUuid(openResp.getBody(), "data");
		ResponseEntity<String> closeResp = rest.exchange(
				"/api/v1/attendance/sessions/" + sessionUuid + "/close",
				HttpMethod.PATCH,
				new HttpEntity<>(headers),
				String.class);
		assertThat(closeResp.getStatusCode())
				.as("close session body=%s", closeResp.getBody())
				.isEqualTo(HttpStatus.OK);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private void createAdmin(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail("admin@it.test");
					user.setPasswordHash(passwordEncoder.encode("EduShift2026!"));
					user.setFirstName("It");
					user.setLastName("Admin");
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.TENANT_ADMIN);
					userRepository.saveAndFlush(user);
					return null;
				}));
	}

	private User createLinkedUser(String slug, String fullName, String email) {
		User u = new User();
		u.setEmail(email);
		u.setPasswordHash(passwordEncoder.encode("EduShift2026!"));
		String[] parts = fullName.split(" ", 2);
		u.setFirstName(parts[0]);
		u.setLastName(parts.length > 1 ? parts[1] : "");
		u.setStatus(UserStatus.ACTIVE);
		u.setEmailVerified(true);
		u.setMfaEnabled(false);
		u.addRole(UserRole.PARENT);
		return tx().execute(s -> userRepository.saveAndFlush(u));
	}

	private String login(String slug, String email, String password) {
		try {
			String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
			HttpHeaders h = new HttpHeaders();
			h.setContentType(MediaType.APPLICATION_JSON);
			h.set("X-Tenant-Slug", slug);
			String json = rest.exchange(
					"/api/v1/auth/login",
					HttpMethod.POST,
					new HttpEntity<>(body, h),
					String.class).getBody();
			return objectMapper().readTree(json).path("accessToken").asText();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
		return new com.fasterxml.jackson.databind.ObjectMapper();
	}

	private String extractPublicUuid(String json, String path) {
		try {
			String s = objectMapper().readTree(json).at("/" + path + "/publicUuid").asText();
			if (s == null || s.isBlank() || s.equals("null")) {
				s = objectMapper().readTree(json).at("/publicUuid").asText();
			}
			return s;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	record Fixture(Tenant tenant, com.edushift.modules.academic.section.entity.Section section,
			List<UUID> guardianUserIds) {
	}
}