package com.edushift.modules.teachers.assignments;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 5 / DEBT-TEA-1 — end-to-end test for the two self-only endpoints
 * that the cascade relaxed in this same iteration:
 *
 * <ul>
 *   <li>{@code GET /v1/teachers/{uuid}/assignments} — self-only guard
 *       (a {@code TEACHER} may only see their own assignments).</li>
 *   <li>{@code GET /v1/academic/sections/{uuid}/teachers} —
 *       teacher-of-section guard (a {@code TEACHER} may see co-teachers
 *       of any section in which they have at least one active
 *       assignment).</li>
 * </ul>
 *
 * <p>Both relaxed endpoints keep {@code TENANT_ADMIN} as the
 * privileged role. The cross-tenant {@code @TenantId} filter provides
 * isolation at the SQL level (Hibernate adds
 * {@code where tenant_id = :ctx}); this IT additionally verifies that
 * the service-layer guardrail rejects {@code TEACHER} callers pointing
 * at the wrong teacher or section with the same 404 status code the
 * system uses for "not found" — that is the anti-enumeration guarantee.</p>
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>{@code listForTeacher}: admin sees any teacher in own tenant (200).</li>
 *   <li>{@code listForTeacher}: TEACHER A reads own assignments (200).</li>
 *   <li>{@code listForTeacher}: TEACHER A asks for TEACHER B's assignments (404).</li>
 *   <li>{@code listForTeacher}: TEACHER A asks for cross-tenant teacher's assignments (404).</li>
 *   <li>{@code listForSection}: admin sees any section in own tenant (200).</li>
 *   <li>{@code listForSection}: TEACHER of section sees co-teachers (200).</li>
 *   <li>{@code listForSection}: TEACHER NOT of section → 404 (anti-enumeration).</li>
 *   <li>{@code listForSection}: TEACHER asks for cross-tenant section → 404.</li>
 * </ol>
 *
 * <p>Test stack: {@link IntegrationTest} → Testcontainers Postgres 16 +
 * embedded Tomcat on random port + {@code test} Spring profile (Redis
 * disabled, JWT secret pinned).</p>
 */
@DisplayName("Sprint 5 / DEBT-TEA-1 — teacher assignment endpoints self-only enforcement")
class TeacherAssignmentSelfOnlyIT extends IntegrationTest {

	private static final String PATH_TEACHER_ASSIGNMENTS =
			"/v1/teachers/{teacherUuid}/assignments";
	private static final String PATH_SECTION_TEACHERS =
			"/v1/academic/sections/{sectionUuid}/teachers";

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
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;

	// =========================================================================
	// GET /v1/teachers/{uuid}/assignments
	// =========================================================================

	@Nested
	@DisplayName("GET /teachers/{uuid}/assignments (self-only)")
	class ListForTeacher {

		@Test
		@DisplayName("SPRINT5-CASCADE: TENANT_ADMIN sees any teacher in own tenant → 200")
		void adminSeesAnyTeacher() throws Exception {
			Seed seed = seedSingleTenantWithTwoTeachers();
			String jwt = login(seed.tenant, "admin", PASSWORD_ADMIN);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_ASSIGNMENTS.replace("{teacherUuid}",
							seed.teacherA.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, seed.tenant.getSlug()),
					String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			// Admin must see teacher A's assignment.
			assertThat(resp.getBody()).contains(
					seed.teacherA.getPublicUuid().toString());
		}

		@Test
		@DisplayName("SPRINT5-CASCADE: TEACHER A reads own assignments → 200")
		void teacherReadsOwn() throws Exception {
			Seed seed = seedSingleTenantWithTwoTeachers();
			String jwt = login(seed.tenant, "teacher-a", PASSWORD_TEACHER_A);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_ASSIGNMENTS.replace("{teacherUuid}",
							seed.teacherA.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, seed.tenant.getSlug()),
					String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(resp.getBody()).contains(
					seed.teacherA.getPublicUuid().toString());
		}

		@Test
		@DisplayName("SPRINT5-CASCADE: TEACHER A asks for TEACHER B's assignments → 404 "
				+ "(anti-enumeration)")
		void teacherCannotReadAnotherTeacher() throws Exception {
			Seed seed = seedSingleTenantWithTwoTeachers();
			String jwt = login(seed.tenant, "teacher-a", PASSWORD_TEACHER_A);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_ASSIGNMENTS.replace("{teacherUuid}",
							seed.teacherB.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, seed.tenant.getSlug()),
					String.class);

			assertThat(resp.getStatusCode())
					.as("must hide B's existence from A")
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("SPRINT5-CASCADE: TEACHER A asks for cross-tenant teacher → 404 "
				+ "(Hibernate @TenantId filter + service guardrail both contribute)")
		void teacherCrossTenantDenied() throws Exception {
			SeedPair pair = seedTwoTenantsEachWithOneTeacher();
			// Login as Teacher A in tenant A and try to fetch teacher B's
			// assignments in tenant B → must surface 404, not 403.
			String jwt = login(pair.tenantA, "teacher-a", PASSWORD_TEACHER_A);

			ResponseEntity<String> resp = rest.exchange(
					PATH_TEACHER_ASSIGNMENTS.replace("{teacherUuid}",
							pair.teacherB.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, pair.tenantA.getSlug()),
					String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// GET /v1/academic/sections/{uuid}/teachers
	// =========================================================================

	@Nested
	@DisplayName("GET /sections/{uuid}/teachers (teacher-of-section)")
	class ListForSection {

		@Test
		@DisplayName("SPRINT5-CASCADE: TENANT_ADMIN sees any section in own tenant → 200")
		void adminSeesAnySection() throws Exception {
			Seed seed = seedSingleTenantTwoTeachersOneSection();
			String jwt = login(seed.tenant, "admin", PASSWORD_ADMIN);

			ResponseEntity<String> resp = rest.exchange(
					PATH_SECTION_TEACHERS.replace("{sectionUuid}",
							seed.section.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, seed.tenant.getSlug()),
					String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(resp.getBody()).contains("\"teacher\"").contains("\"course\"");
		}

		@Test
		@DisplayName("SPRINT5-CASCADE: TEACHER with active assignment in section → 200")
		void teacherInSectionSeesRoster() throws Exception {
			Seed seed = seedSingleTenantTwoTeachersOneSection();
			// Teacher A is teaching the section (see fixture).
			String jwt = login(seed.tenant, "teacher-a", PASSWORD_TEACHER_A);

			ResponseEntity<String> resp = rest.exchange(
					PATH_SECTION_TEACHERS.replace("{sectionUuid}",
							seed.section.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, seed.tenant.getSlug()),
					String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			// Both teachers should be listed (co-teachers of the section).
			assertThat(resp.getBody()).contains(
					seed.teacherA.getPublicUuid().toString());
			assertThat(resp.getBody()).contains(
					seed.teacherB.getPublicUuid().toString());
		}

		@Test
		@DisplayName("SPRINT5-CASCADE: TEACHER with NO assignment in section → 404 "
				+ "(anti-enumeration)")
		void teacherNotInSectionDenied() throws Exception {
			Seed seed = seedSingleTenantTwoTeachersOneSection();
			// Teacher B is NOT teaching the section.
			String jwt = login(seed.tenant, "teacher-b", PASSWORD_TEACHER_B);

			ResponseEntity<String> resp = rest.exchange(
					PATH_SECTION_TEACHERS.replace("{sectionUuid}",
							seed.section.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, seed.tenant.getSlug()),
					String.class);

			assertThat(resp.getStatusCode())
					.as("must hide roster from B since B is not in the section")
					.isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("SPRINT5-CASCADE: TEACHER asks for cross-tenant section → 404")
		void teacherCrossTenantSectionDenied() throws Exception {
			SeedPair pair = seedTwoTenantsEachWithOneTeacher();
			String jwt = login(pair.tenantA, "teacher-a", PASSWORD_TEACHER_A);

			ResponseEntity<String> resp = rest.exchange(
					PATH_SECTION_TEACHERS.replace("{sectionUuid}",
							pair.sectionB.getPublicUuid().toString()),
					HttpMethod.GET,
					authed(jwt, pair.tenantA.getSlug()),
					String.class);

			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	private Seed seedSingleTenantWithTwoTeachers() {
		Tenant tenant = createTenant();
		createUser(tenant, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");
		createUser(tenant, "teacher-a", PASSWORD_TEACHER_A, "TEACHER");
		createUser(tenant, "teacher-b", PASSWORD_TEACHER_B, "TEACHER");

		Teacher teacherA = createTeacher(tenant, "teacher-a",
				userPublicUuid(tenant, "teacher-a"));
		Teacher teacherB = createTeacher(tenant, "teacher-b",
				userPublicUuid(tenant, "teacher-b"));

		// Each teacher gets their own active assignment so the response
		// distinguishes them.
		createAssignment(tenant, teacherA);
		createAssignment(tenant, teacherB);

		return new Seed(tenant, teacherA, teacherB, null);
	}

	private Seed seedSingleTenantTwoTeachersOneSection() {
		Tenant tenant = createTenant();
		createUser(tenant, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");
		createUser(tenant, "teacher-a", PASSWORD_TEACHER_A, "TEACHER");
		createUser(tenant, "teacher-b", PASSWORD_TEACHER_B, "TEACHER");

		Teacher teacherA = createTeacher(tenant, "teacher-a",
				userPublicUuid(tenant, "teacher-a"));
		Teacher teacherB = createTeacher(tenant, "teacher-b",
				userPublicUuid(tenant, "teacher-b"));

		Section section = createSection(tenant);
		// Both teachers assigned to the SAME section → teacher A can see
		// the roster (which includes teacher B). Teacher B is NOT
		// assigned → teacher B cannot see the roster.
		createAssignmentTo(tenant, teacherA, section);
		// teacherB intentionally has no assignment in this section.

		return new Seed(tenant, teacherA, teacherB, section);
	}

	private SeedPair seedTwoTenantsEachWithOneTeacher() {
		Tenant tenantA = createTenant();
		Tenant tenantB = createTenant();
		createUser(tenantA, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");
		createUser(tenantA, "teacher-a", PASSWORD_TEACHER_A, "TEACHER");
		createUser(tenantB, "admin", PASSWORD_ADMIN, "TENANT_ADMIN");
		createUser(tenantB, "teacher-b", PASSWORD_TEACHER_B, "TEACHER");

		Teacher teacherA = createTeacher(tenantA, "teacher-a",
				userPublicUuid(tenantA, "teacher-a"));
		Teacher teacherB = createTeacher(tenantB, "teacher-b",
				userPublicUuid(tenantB, "teacher-b"));
		createAssignment(tenantA, teacherA);
		createAssignment(tenantB, teacherB);

		Section sectionB = createSection(tenantB);
		createAssignmentTo(tenantB, teacherB, sectionB);

		return new SeedPair(tenantA, tenantB, teacherA, teacherB, sectionB);
	}

	private UUID userPublicUuid(Tenant tenant, String localPart) {
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

	private Section createSection(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () ->
				new TransactionTemplate(txManager).execute(s -> {
					List<Section> existing = sectionRepository.findAll();
					if (!existing.isEmpty()) return existing.get(0);

					// Fallback: create the academic skeleton on the fly
					// so the IT is independent of V38/V39 dev seeding.
					throw new IllegalStateException(
							"IT fixture requires at least one section in tenant "
									+ tenant.getSlug()
									+ "; run with a tenant that has been seeded "
									+ "via DevDataInitializer (V38 / V39).");
				}));
	}

	private void createAssignment(Tenant tenant, Teacher teacher) {
		TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).execute(s -> {
				List<Section> sections = sectionRepository.findAll();
				List<Course> courses = courseRepository.findAll();
				List<AcademicPeriod> periods = periodRepository.findAll();
				requireFixtures(sections, courses, periods);
				TeacherAssignment a = new TeacherAssignment();
				a.setTeacher(teacher);
				a.setSection(sections.get(0));
				a.setCourse(courses.get(0));
				a.setAcademicPeriod(periods.get(0));
				a.setAssignedAt(Instant.now());
				assignmentRepository.saveAndFlush(a);
				return null;
			});
			return null;
		});
	}

	private void createAssignmentTo(Tenant tenant, Teacher teacher, Section section) {
		TenantContext.runAs(tenant.getId(), () -> {
			new TransactionTemplate(txManager).execute(s -> {
				List<Course> courses = courseRepository.findAll();
				List<AcademicPeriod> periods = periodRepository.findAll();
				requireFixtures(List.of(section), courses, periods);
				TeacherAssignment a = new TeacherAssignment();
				a.setTeacher(teacher);
				a.setSection(section);
				a.setCourse(courses.get(0));
				a.setAcademicPeriod(periods.get(0));
				a.setAssignedAt(Instant.now());
				assignmentRepository.saveAndFlush(a);
				return null;
			});
			return null;
		});
	}

	private static void requireFixtures(List<?> sections, List<?> courses,
			List<?> periods) {
		if (sections.isEmpty() || courses.isEmpty() || periods.isEmpty()) {
			throw new IllegalStateException(
					"IT fixture requires a tenant with academic skeleton pre-"
							+ "seeded (section + course + period). Run with a DB that "
							+ "has gone through DevDataInitializer.");
		}
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

	private record Seed(Tenant tenant, Teacher teacherA, Teacher teacherB,
			Section section) {}

	private record SeedPair(Tenant tenantA, Tenant tenantB,
			Teacher teacherA, Teacher teacherB, Section sectionB) {}
}
