package com.edushift.modules.schedule.timeslot;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
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
import java.time.LocalTime;
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
 * Sprint 5 / DEBT-TEA-1 — end-to-end test for the relaxed
 * {@code GET /api/v1/teachers/{uuid}/schedule} endpoint.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>{@code TENANT_ADMIN} sees any teacher in own tenant (regression
 *       of the pre-DEBT-TEA-1 behaviour) → 200.</li>
 *   <li>{@code TEACHER} bearer can GET their OWN schedule → 200.</li>
 *   <li>{@code TEACHER} bearer asking for ANOTHER teacher's schedule →
 *       404 (anti-enumeration: same status code as a non-existent
 *       teacher).</li>
 * </ol>
 *
 * <p>Test stack: {@link IntegrationTest} → Testcontainers Postgres 16 +
 * embedded Tomcat on random port + {@code test} Spring profile (Redis
 * disabled, JWT secret pinned).</p>
 */
@DisplayName("Sprint 5 / DEBT-TEA-1 — teacher self-only schedule endpoint")
class TimeSlotSelfServiceIT extends IntegrationTest {

	private static final String PASSWORD_ADMIN = "AdminPass-1!";
	private static final String PASSWORD_TEACHER_A = "TeacherPass-A!";
	private static final String PASSWORD_TEACHER_B = "TeacherPass-B!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private AcademicPeriodRepository periodRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private TimeSlotRepository timeSlotRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;

	@Test
	@DisplayName("SPRINT5-CASCADE: TENANT_ADMIN sees any teacher in own tenant → 200")
	void adminSeesAnyTeacher() throws Exception {
		Seed seed = seedSingleTenantWithOneTeacher();
		String jwt = login(seed.tenant, seed.tenant.getSlug() + "-admin",
				PASSWORD_ADMIN);

		ResponseEntity<String> resp = rest.exchange(
				"/v1/teachers/" + seed.teacher.getPublicUuid() + "/schedule",
				HttpMethod.GET,
				authed(jwt, seed.tenant.getSlug()),
				String.class);

		assertThat(resp.getStatusCode())
				.as("admin must see the teacher's schedule")
				.isEqualTo(HttpStatus.OK);
		assertThat(resp.getBody())
				.contains("\"teacher\"")
				.contains("\"course\"")
				.contains("\"section\"")
				.contains("\"dayOfWeek\"");
	}

	@Test
	@DisplayName("SPRINT5-CASCADE: TEACHER bearer A reads own schedule → 200")
	void teacherReadsOwn() throws Exception {
		Seed seed = seedSingleTenantWithOneTeacher();
		String jwt = login(seed.tenant, seed.tenant.getSlug() + "-teacher-a",
				PASSWORD_TEACHER_A);

		ResponseEntity<String> resp = rest.exchange(
				"/v1/teachers/" + seed.teacher.getPublicUuid() + "/schedule",
				HttpMethod.GET,
				authed(jwt, seed.tenant.getSlug()),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	@DisplayName("SPRINT5-CASCADE: TEACHER A asks for TEACHER B's schedule → 404 "
			+ "(anti-enumeration)")
	void teacherCannotReadAnotherTeacher() throws Exception {
		Seed seed = seedSingleTenantWithTwoTeachers();
		String jwt = login(seed.tenant, seed.tenant.getSlug() + "-teacher-a",
				PASSWORD_TEACHER_A);

		ResponseEntity<String> resp = rest.exchange(
				"/v1/teachers/" + seed.teacherB.getPublicUuid() + "/schedule",
				HttpMethod.GET,
				authed(jwt, seed.tenant.getSlug()),
				String.class);

		assertThat(resp.getStatusCode())
				.as("must hide B's existence from A")
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	private Seed seedSingleTenantWithOneTeacher() {
		Tenant tenant = createTenant();
		createUser(tenant, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");
		createUser(tenant, "teacher-a", PASSWORD_TEACHER_A, "TEACHER");
		Teacher teacher = createTeacher(tenant, "teacher-a",
				teacherUserPublicUuid(tenant, "teacher-a"));
		createAssignmentWithOneSlot(tenant, teacher);
		return new Seed(tenant, teacher, null);
	}

	private Seed seedSingleTenantWithTwoTeachers() {
		Tenant tenant = createTenant();
		createUser(tenant, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");
		createUser(tenant, "teacher-a", PASSWORD_TEACHER_A, "TEACHER");
		createUser(tenant, "teacher-b", PASSWORD_TEACHER_B, "TEACHER");
		Teacher teacherA = createTeacher(tenant, "teacher-a",
				teacherUserPublicUuid(tenant, "teacher-a"));
		Teacher teacherB = createTeacher(tenant, "teacher-b",
				teacherUserPublicUuid(tenant, "teacher-b"));
		createAssignmentWithOneSlot(tenant, teacherA);
		createAssignmentWithOneSlot(tenant, teacherB);
		return new Seed(tenant, teacherA, teacherB);
	}

	private UUID teacherUserPublicUuid(Tenant tenant, String localPart) {
		return TenantContext.runAs(tenant.getId(), () -> userRepository
				.findByEmailAndTenantId(
						tenant.getSlug() + "-" + localPart + "@self.test",
						tenant.getId())
				.map(User::getPublicUuid).orElseThrow());
	}

	private Tenant createTenant() {
		Tenant t = new Tenant();
		t.setSlug("it-self-" + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		t.setPlan(TenantPlan.TRIAL);
		t.setPlanId(jdbcTemplate.queryForObject(
				"select id from edushift.platform_plans where code = ?",
				UUID.class, "TRIAL"));
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
					t.setDocumentType(
							com.edushift.modules.students.entity.DocumentType.DNI);
					t.setDocumentNumber(UUID.randomUUID().toString().substring(0, 8));
					t.setBirthDate(LocalDate.of(1985, 1, 1));
					return teacherRepository.saveAndFlush(t);
				}));
	}

	private void createAssignmentWithOneSlot(Tenant tenant, Teacher teacher) {
		TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).execute(s -> {
				List<Section> sections = sectionRepository.findAll();
				List<Course> courses = courseRepository.findAll();
				List<AcademicPeriod> periods = periodRepository.findAll();
				if (sections.isEmpty() || courses.isEmpty() || periods.isEmpty()) {
					throw new IllegalStateException(
							"IT fixture assumes a tenant pre-seeded with at least "
									+ "one section, course, period; the dev seed "
									+ "(V38, V39) should provide them. If this fails "
									+ "on a fresh DB, run DevDataInitializer first.");
				}

				TeacherAssignment a = new TeacherAssignment();
				a.setTeacher(teacher);
				a.setSection(sections.get(0));
				a.setCourse(courses.get(0));
				a.setAcademicPeriod(periods.get(0));
				a.setAssignedAt(Instant.now());
				a = assignmentRepository.saveAndFlush(a);

				TimeSlot slot = new TimeSlot();
				slot.setTeacherAssignment(a);
				slot.setDayOfWeek((short) 1);
				slot.setStartTime(LocalTime.of(8, 0));
				slot.setEndTime(LocalTime.of(9, 0));
				slot.setClassroom("Aula 1");
				timeSlotRepository.saveAndFlush(slot);
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

	private record Seed(Tenant tenant, Teacher teacher, Teacher teacherB) {}
}
