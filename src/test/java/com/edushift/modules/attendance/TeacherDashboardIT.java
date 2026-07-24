package com.edushift.modules.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 9B / BE-9B.1 — end-to-end coverage of
 * {@code GET /v1/dashboard/teacher}.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li><strong>teacherWithAssignments</strong> — a TEACHER with one active
 *       assignment gets 200 with a non-empty {@code mySectionsToday}
 *       entry for that section. The dashboard includes the teacher in
 *       the count of students enrolled.</li>
 *   <li><strong>teacherWithoutAssignments</strong> — a TEACHER with no
 *       active assignments gets 200 with all-empty arrays (defensive
 *       empty payload, not a 404).</li>
 *   <li><strong>tenantAdminWithoutTeacherLink</strong> — a TENANT_ADMIN
 *       with no linked teacher gets 200 with all-empty arrays (the
 *       service returns the empty payload early in the pipeline).</li>
 *   <li><strong>crossTenantIsolation</strong> — a TEACHER in tenant A
 *       sees only tenant A's data; the same teacher cannot see any
 *       section from tenant B in the response (tenant_id is hard-coded
 *       in the SQL via the JWT-derived tenant context).</li>
 *   <li><strong>anonymous</strong> — missing JWT returns 401.</li>
 * </ol>
 *
 * <p>Test stack: {@link IntegrationTest} → Testcontainers Postgres 16 +
 * embedded Tomcat on a random port + {@code test} Spring profile
 * (Redis disabled, JWT secret pinned).</p>
 */
@DisplayName("Sprint 9B / BE-9B.1 — teacher dashboard endpoint")
class TeacherDashboardIT extends IntegrationTest {

	private static final String PATH_TEACHER_DASHBOARD = "/v1/dashboard/teacher";
	private static final String PASSWORD_ADMIN = "AdminPass-1!";
	private static final String PASSWORD_TEACHER = "TeacherPass-1!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private AcademicPeriodRepository periodRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;

	@Nested
	@DisplayName("GET /dashboard/teacher")
	class Get {

		@Test
		@DisplayName("SPRINT9B: TEACHER with one active assignment → 200 with non-empty mySectionsToday")
		void teacherWithAssignments() throws Exception {
			Seed seed = seedSingleTenantWithTeacherAndSection("dash-ok-");

			String jwt = login(seed.tenant(), "teacher", PASSWORD_TEACHER);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_DASHBOARD, HttpMethod.GET,
					authed(jwt, seed.tenant().getSlug()), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data).isNotNull();
			assertThat(data.get("mySectionsToday").isArray()).isTrue();
			assertThat(data.get("mySectionsToday").size())
					.as("teacher has one active assignment -> 1 entry")
					.isEqualTo(1);
			JsonNode first = data.get("mySectionsToday").get(0);
			assertThat(first.get("sectionPublicUuid").asText())
					.isEqualTo(seed.section().getPublicUuid().toString());
			assertThat(first.get("sectionName").asText())
					.isEqualTo(seed.section().getName());
			assertThat(first.get("enrolledStudents").asLong())
					.as("the seeded enrollment must be reflected in the KPI")
					.isEqualTo(1L);
		}

		@Test
		@DisplayName("SPRINT9B: TEACHER with no active assignments → 200 with empty arrays")
		void teacherWithoutAssignments() throws Exception {
			Seed seed = seedSingleTenantWithTeacherAndSection("dash-empty-");
			// Soft-end the only assignment so the teacher has none.
			softEndAllAssignments(seed.tenant(), seed.teacher());

			String jwt = login(seed.tenant(), "teacher", PASSWORD_TEACHER);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_DASHBOARD, HttpMethod.GET,
					authed(jwt, seed.tenant().getSlug()), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data.get("mySectionsToday").size()).isEqualTo(0);
			assertThat(data.get("topAbsentSections").size()).isEqualTo(0);
			assertThat(data.get("recentClosedSessions").size()).isEqualTo(0);
			assertThat(data.get("openSessions").asLong()).isEqualTo(0L);
		}

		@Test
		@DisplayName("SPRINT9B: TENANT_ADMIN without teacher link → 200 with empty arrays")
		void tenantAdminWithoutTeacherLink() throws Exception {
			Tenant tenant = createTenant("dash-admin-");
			createUser(tenant, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");

			String jwt = login(tenant, "admin", PASSWORD_ADMIN);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_DASHBOARD, HttpMethod.GET,
					authed(jwt, tenant.getSlug()), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			assertThat(data.get("mySectionsToday").size()).isEqualTo(0);
			assertThat(data.get("topAbsentSections").size()).isEqualTo(0);
		}

		@Test
		@DisplayName("SPRINT9B: cross-tenant — teacher in tenant A cannot see tenant B's data")
		void crossTenantIsolation() throws Exception {
			SeedPair pair = seedTwoTenantsEachWithTeacherAndSection("dash-cross-");
			String jwt = login(pair.tenantA(), "teacher", PASSWORD_TEACHER);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_DASHBOARD, HttpMethod.GET,
					authed(jwt, pair.tenantA().getSlug()), String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
			// Section from tenant A is present.
			assertThat(data.get("mySectionsToday").size()).isEqualTo(1);
			String visibleUuid = data.get("mySectionsToday").get(0)
					.get("sectionPublicUuid").asText();
			assertThat(visibleUuid)
					.as("must show tenant A's section, not tenant B's")
					.isEqualTo(pair.sectionA().getPublicUuid().toString());
			assertThat(visibleUuid)
					.as("must not leak tenant B's section uuid")
					.isNotEqualTo(pair.sectionB().getPublicUuid().toString());
		}

		@Test
		@DisplayName("SPRINT9B: anonymous (no JWT) → 401")
		void anonymous() {
			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_DASHBOARD, HttpMethod.GET,
					HttpEntity.EMPTY, String.class);
			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	private Seed seedSingleTenantWithTeacherAndSection(String slugPrefix) {
		Tenant tenant = createTenant(slugPrefix);
		createUser(tenant, "teacher", PASSWORD_TEACHER, "TEACHER");
		Teacher teacher = createTeacher(tenant, "teacher",
				userPublicUuid(tenant, "teacher"));
		Section section = createSectionWithEnrolledStudent(tenant);
		createAssignment(tenant, teacher, section);
		return new Seed(tenant, teacher, section);
	}

	private SeedPair seedTwoTenantsEachWithTeacherAndSection(String slugPrefix) {
		Tenant tenantA = createTenant(slugPrefix + "a-");
		Tenant tenantB = createTenant(slugPrefix + "b-");
		createUser(tenantA, "teacher", PASSWORD_TEACHER, "TEACHER");
		createUser(tenantB, "teacher", PASSWORD_TEACHER, "TEACHER");
		Teacher teacherA = createTeacher(tenantA, "teacher",
				userPublicUuid(tenantA, "teacher"));
		Teacher teacherB = createTeacher(tenantB, "teacher",
				userPublicUuid(tenantB, "teacher"));
		Section sectionA = createSectionWithEnrolledStudent(tenantA);
		Section sectionB = createSectionWithEnrolledStudent(tenantB);
		createAssignment(tenantA, teacherA, sectionA);
		createAssignment(tenantB, teacherB, sectionB);
		return new SeedPair(tenantA, tenantB, teacherA, teacherB, sectionA, sectionB);
	}

	private void softEndAllAssignments(Tenant tenant, Teacher teacher) {
		TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).execute(s -> {
				jdbcTemplate.update(
						"update edushift.teacher_assignments set unassigned_at = ? "
								+ "where teacher_id = ? and unassigned_at is null",
						java.sql.Timestamp.from(Instant.now()), teacher.getId());
				return null;
			});
			return null;
		});
	}

	private UUID userPublicUuid(Tenant tenant, String localPart) {
		return TenantContext.runAs(tenant.getId(), () -> userRepository
				.findByEmailAndTenantId(
						tenant.getSlug() + "-" + localPart + "@self.test",
						tenant.getId())
				.map(User::getPublicUuid).orElseThrow());
	}

	private Tenant createTenant(String prefix) {
		Tenant t = new Tenant();
		t.setSlug(prefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		t.setPlan(TenantPlan.BASIC);
		t.setPlanId(jdbcTemplate.queryForObject(
				"select id from edushift.platform_plans where code = ?",
				UUID.class, "BASIC"));
		return new TransactionTemplate(txManager).execute(s ->
				tenantRepository.saveAndFlush(t));
	}

	private void createUser(Tenant tenant, String localPart, String rawPassword,
			String role) {
		TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).execute(s -> {
				User u = new User();
				u.setEmail(tenant.getSlug() + "-" + localPart + "@self.test");
				u.setPasswordHash(passwordEncoder.encode(rawPassword));
				u.setFirstName("It");
				u.setLastName(localPart);
				u.setStatus(UserStatus.ACTIVE);
				u.setEmailVerified(true);
				u.setMfaEnabled(false);
				setRoles(u, role);
				userRepository.saveAndFlush(u);
				return null;
			});
			return null;
		});
	}

	private Teacher createTeacher(Tenant tenant, String localPart, UUID userPublicUuid) {
		return TenantContext.runAs(tenant.getId(), () ->
				new TransactionTemplate(txManager).execute(s -> {
					Teacher t = new Teacher();
					t.setFirstName(localPart);
					t.setLastName("Self");
					t.setEmail(localPart + "." + tenant.getSlug() + "@self.test");
					t.setEmploymentStatus(EmploymentStatus.ACTIVE);
					t.setUserId(userPublicUuid);
					t.setDocumentType(DocumentType.DNI);
					t.setDocumentNumber(UUID.randomUUID().toString().substring(0, 8));
					t.setBirthDate(LocalDate.of(1985, 1, 1));
					t.setGender(Gender.MALE);
					return teacherRepository.saveAndFlush(t);
				}));
	}

	/**
	 * Builds the minimum academic skeleton (level → year → grade →
	 * section + course + period) inline so the IT is independent of
	 * the V38/V39 dev seed (which the live verify exercises).
	 */
	private Section createSectionWithEnrolledStudent(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () ->
				new TransactionTemplate(txManager).execute(s -> {
					AcademicLevel level = newLevel(tenant);
					AcademicYear year = newYear(tenant);
					Grade grade = newGrade(tenant, level, year);
					Section section = newSection(tenant, year, grade);
					Course course = newCourse(tenant);
					AcademicPeriod period = newPeriod(tenant, year);

					Student student = newStudent(tenant);
					enrollStudent(tenant, student, section, year);

					return section;
				}));
	}

	private AcademicLevel newLevel(Tenant tenant) {
		AcademicLevel l = new AcademicLevel();
		l.setTenantId(tenant.getId());
		l.setName("IT Level " + UUID.randomUUID().toString().substring(0, 6));
		l.setCode("ITL_" + UUID.randomUUID().toString().replace("-", "")
				.substring(0, 6).toUpperCase());
		l.setOrdinal(1);
		return levelRepository.saveAndFlush(l);
	}

	private AcademicYear newYear(Tenant tenant) {
		AcademicYear y = new AcademicYear();
		y.setTenantId(tenant.getId());
		y.setName("IT Year " + UUID.randomUUID().toString().substring(0, 6));
		y.setStartDate(LocalDate.now().minusDays(30));
		y.setEndDate(LocalDate.now().plusDays(330));
		y.setStatus(AcademicYearStatus.ACTIVE);
		return yearRepository.saveAndFlush(y);
	}

	private Grade newGrade(Tenant tenant, AcademicLevel level, AcademicYear year) {
		Grade g = new Grade();
		g.setTenantId(tenant.getId());
		g.setLevel(level);
		g.setName("IT Grade " + UUID.randomUUID().toString().substring(0, 6));
		g.setOrdinal(1);
		return gradeRepository.saveAndFlush(g);
	}

	private Section newSection(Tenant tenant, AcademicYear year, Grade grade) {
		Section s = new Section();
		s.setTenantId(tenant.getId());
		s.setAcademicYear(year);
		s.setGrade(grade);
		s.setName("A");
		s.setDisplayOrder(1);
		return sectionRepository.saveAndFlush(s);
	}

	private Course newCourse(Tenant tenant) {
		Course c = new Course();
		c.setTenantId(tenant.getId());
		c.setCode("ITC_" + UUID.randomUUID().toString().replace("-", "")
				.substring(0, 6).toUpperCase());
		c.setName("IT Course " + UUID.randomUUID().toString().substring(0, 6));
		c.setIsActive(true);
		return courseRepository.saveAndFlush(c);
	}

	private AcademicPeriod newPeriod(Tenant tenant, AcademicYear year) {
		AcademicPeriod p = new AcademicPeriod();
		p.setTenantId(tenant.getId());
		p.setAcademicYear(year);
		p.setName("IT Period " + UUID.randomUUID().toString().substring(0, 6));
		p.setPeriodType(PeriodType.TRIMESTRE);
		p.setOrdinal(1);
		p.setStartDate(LocalDate.now().minusDays(15));
		p.setEndDate(LocalDate.now().plusDays(60));
		return periodRepository.saveAndFlush(p);
	}

	private Student newStudent(Tenant tenant) {
		Student s = new Student();
		s.setTenantId(tenant.getId());
		s.setDocumentType(DocumentType.DNI);
		s.setDocumentNumber(UUID.randomUUID().toString().substring(0, 8));
		s.setFirstName("Stu");
		s.setLastName("Dent");
		s.setBirthDate(LocalDate.of(2015, 1, 1));
		s.setGender(Gender.MALE);
		return studentRepository.saveAndFlush(s);
	}

	private void enrollStudent(Tenant tenant, Student student, Section section,
			AcademicYear year) {
		StudentEnrollment enrollment = new StudentEnrollment();
		enrollment.setTenantId(tenant.getId());
		enrollment.setStudent(student);
		enrollment.setSection(section);
		enrollment.setAcademicYear(year);
		enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
		enrollment.setEnrolledAt(LocalDate.now());
		enrollmentRepository.saveAndFlush(enrollment);
	}

	private void createAssignment(Tenant tenant, Teacher teacher, Section section) {
		TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).execute(s -> {
				// Reuse the course/period created for the section's
				// academic year; safest path: just re-fetch the first
				// course and period in this tenant.
				Course course = courseRepository.findAll().get(0);
				AcademicPeriod period = periodRepository.findAll().get(0);
				TeacherAssignment a = new TeacherAssignment();
				a.setTeacher(teacher);
				a.setSection(section);
				a.setCourse(course);
				a.setAcademicPeriod(period);
				a.setAssignedAt(Instant.now());
				assignmentRepository.saveAndFlush(a);
				return null;
			});
			return null;
		});
	}

	// ---- helpers -------------------------------------------------------

	private String login(Tenant tenant, String localPart, String rawPassword) {
		try {
			String body = "{\"email\":\"" + tenant.getSlug() + "-" + localPart
					+ "@self.test\",\"password\":\"" + rawPassword + "\"}";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("X-Tenant-Slug", tenant.getSlug());
			JsonNode root = objectMapper.readTree(rest.exchange(
					"/v1/auth/login", HttpMethod.POST,
					new HttpEntity<>(body, headers), String.class).getBody());
			return root.get("accessToken").asText();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static HttpEntity<Void> authed(String jwt, String tenantSlug) {
		HttpHeaders h = new HttpHeaders();
		h.setBearerAuth(jwt);
		h.set("X-Tenant-Slug", tenantSlug);
		return new HttpEntity<>(h);
	}

	private static void setRoles(User u, String role) {
		try {
			Field f = User.class.getDeclaredField("roles");
			f.setAccessible(true);
			f.set(u, new String[] { role });
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private record Seed(Tenant tenant, Teacher teacher, Section section) {}

	private record SeedPair(Tenant tenantA, Tenant tenantB,
			Teacher teacherA, Teacher teacherB,
			Section sectionA, Section sectionB) {}
}
