package com.edushift.modules.tasks;

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
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.edushift.modules.tasks.submission.repository.SubmissionRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LMS RBAC integration test (Sprint 7a / BE-7a.5).
 *
 * <p>Exercises the granular {@code LMS_*} authorities added in BE-7a.3
 * end-to-end, against the live HTTP filter chain. Verifies that the
 * {@link com.edushift.shared.security.LmsRoleAuthorityMapper} is wired
 * correctly into the JWT filter and that controllers' coarse
 * {@code @PreAuthorize("hasAuthority(...)")} gates fire as expected.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li><strong>RBAC-1</strong> (spec): STUDENT tries to PATCH
 *       {@code /submissions/{uuid}/grade} → 403 (no {@code LMS_TASK_GRADE}).</li>
 *   <li><strong>RBAC-2</strong> (spec): STAFF tries to POST
 *       {@code /tasks/{uuid}/submissions} → 403 (no {@code LMS_TASK_SUBMIT}).</li>
 *   <li><strong>RBAC-3</strong> (spec): PARENT submits on behalf of a
 *       student → 201 (has {@code LMS_TASK_SUBMIT}; parent-link DB
 *       check is v1-stub, see DEBT-7A-23).</li>
 *   <li><strong>RBAC-4</strong> (extra): TEACHER tries to submit on
 *       behalf of a student → 403 (TEACHER has no
 *       {@code LMS_TASK_SUBMIT}).</li>
 *   <li><strong>RBAC-5</strong> (control positive): TEACHER grades a
 *       submission → 200 (has {@code LMS_TASK_GRADE}).</li>
 *   <li><strong>RBAC-6</strong> (control positive): STUDENT GETs the
 *       material list → 200 (has {@code LMS_MATERIAL_READ}).</li>
 * </ol>
 *
 * <h3>Why synthetic JWTs instead of real logins</h3>
 * Real logins are covered by the cross-tenant ITs. This test only cares
 * about which {@code LMS_*} authority a role maps to, so we mint a
 * token directly via {@link JwtService#issueAccessToken} with the
 * desired role set, avoiding the password-encoder + login-flow noise.
 */
@DisplayName("LMS RBAC (BE-7a.5) — granular authority gates")
class LmsRbacIT extends IntegrationTest {

	private static final String TASKS_SUBS_BASE = "/v1/tasks/{task}/submissions";
	private static final String SUBMISSIONS_BASE = "/v1/submissions";
	private static final String SECTIONS_MATERIALS_BASE = "/v1/sections/{section}/materials";

	@Autowired private TestRestTemplate rest;
	@Autowired private JwtService jwtService;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private SubmissionRepository submissionRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private PlatformTransactionManager txManager;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Spec scenarios
	// =========================================================================

	@Nested
	@DisplayName("Spec scenarios (BE-7a.3 §✅ Criterios de aceptación)")
	class SpecScenarios {

		@Test
		@DisplayName("RBAC-1: STUDENT PATCH /submissions/{uuid}/grade → 403")
		void studentCannotGrade() {
			Fixture f = setup();
			Submission sub = f.submission();
			// STUDENT token
			String bearer = bearerFor(f.student(), f.tenant(), Set.of("STUDENT"));

			ResponseEntity<String> response = doPatch(
					SUBMISSIONS_BASE + "/" + sub.getPublicUuid() + "/grade",
					bearer,
					"{\"grade\":99,\"feedback\":\"hack\"}");

			assertThat(response.getStatusCode())
					.as("STUDENT has no LMS_TASK_GRADE; controller @PreAuthorize must reject")
					.isEqualTo(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("RBAC-2: STAFF POST /tasks/{uuid}/submissions → 403")
		void staffCannotSubmit() {
			Fixture f = setup();
			// STAFF token (no LMS_TASK_SUBMIT)
			String bearer = bearerFor(f.staff(), f.tenant(), Set.of("STAFF"));

			String body = String.format(
					"{\"studentPublicUuid\":\"%s\",\"textBody\":\"staff attempt\"}",
					f.student().getPublicUuid());
			ResponseEntity<String> response = doPost(
					TASKS_SUBS_BASE.replace("{task}", f.task().getPublicUuid().toString()),
					bearer,
					body);

			assertThat(response.getStatusCode())
					.as("STAFF has no LMS_TASK_SUBMIT; controller @PreAuthorize must reject")
					.isEqualTo(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("RBAC-3: PARENT POST /tasks/{uuid}/submissions on behalf of student → 201 (or 200 if re-submit)")
		void parentCanSubmitOnBehalf() {
			Fixture f = setup();
			// PARENT token (LMS_TASK_SUBMIT yes; parent-link DB check is v1-stub, see DEBT-7A-23)
			String bearer = bearerFor(f.parent(), f.tenant(), Set.of("PARENT"));

			String body = String.format(
					"{\"studentPublicUuid\":\"%s\",\"textBody\":\"submitted by parent\"}",
					f.student().getPublicUuid());
			ResponseEntity<String> response = doPost(
					TASKS_SUBS_BASE.replace("{task}", f.task().getPublicUuid().toString()),
					bearer,
					body);

			// The shared setup() pre-creates a submission for the same
			// (task, student) pair, so this call resolves as an idempotent
			// re-submit (200 OK with status=SUBMITTED). The coarse RBAC
			// gate (LMS_TASK_SUBMIT) still passes for PARENT, which is
			// what this test is asserting.
			assertThat(response.getStatusCode())
					.as("PARENT has LMS_TASK_SUBMIT; coarse gate lets it through (200 if re-submit, 201 if first submit)")
					.isIn(HttpStatus.OK, HttpStatus.CREATED);
			assertThat(response.getBody()).contains("\"status\":\"SUBMITTED\"");
		}
	}

	// =========================================================================
	// Extra scenarios (matrix coverage beyond the spec)
	// =========================================================================

	@Nested
	@DisplayName("Extra matrix coverage (control positives + negative cases)")
	class Extra {

		@Test
		@DisplayName("RBAC-4: TEACHER POST /tasks/{uuid}/submissions on behalf of student → 403")
		void teacherCannotSubmit() {
			Fixture f = setup();
			// TEACHER token (no LMS_TASK_SUBMIT per the BE-7a.3 matrix)
			String bearer = bearerFor(f.teacher(), f.tenant(), Set.of("TEACHER"));

			String body = String.format(
					"{\"studentPublicUuid\":\"%s\",\"textBody\":\"teacher attempt\"}",
					f.student().getPublicUuid());
			ResponseEntity<String> response = doPost(
					TASKS_SUBS_BASE.replace("{task}", f.task().getPublicUuid().toString()),
					bearer,
					body);

			assertThat(response.getStatusCode())
					.as("TEACHER has no LMS_TASK_SUBMIT in the spec matrix; must be 403")
					.isEqualTo(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("RBAC-5: TEACHER PATCH /submissions/{uuid}/grade → 200 (control positive)")
		void teacherCanGrade() {
			Fixture f = setup();
			Submission sub = f.submission();
			String bearer = bearerFor(f.teacher(), f.tenant(), Set.of("TEACHER"));

			ResponseEntity<String> response = doPatch(
					SUBMISSIONS_BASE + "/" + sub.getPublicUuid() + "/grade",
					bearer,
					"{\"grade\":85,\"feedback\":\"good\"}");

			assertThat(response.getStatusCode())
					.as("TEACHER has LMS_TASK_GRADE; coarse gate passes")
					.isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).contains("\"status\":\"GRADED\"");
		}

		@Test
		@DisplayName("RBAC-6: STUDENT GET /sections/{uuid}/materials → 200 (LMS_MATERIAL_READ)")
		void studentCanReadMaterials() {
			Fixture f = setup();
			String bearer = bearerFor(f.student(), f.tenant(), Set.of("STUDENT"));

			ResponseEntity<String> response = doGet(
					SECTIONS_MATERIALS_BASE.replace("{section}",
							f.section().getPublicUuid().toString()),
					bearer);

			assertThat(response.getStatusCode())
					.as("STUDENT has LMS_MATERIAL_READ; should see (possibly empty) listing")
					.isEqualTo(HttpStatus.OK);
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

	private String bearerFor(User user, Tenant tenant, Set<String> roles) {
		return jwtService.issueAccessToken(user, tenant, roles);
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

	private ResponseEntity<String> doPatch(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PATCH,
				new HttpEntity<>(body, headers), String.class);
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	record Fixture(
			Tenant tenant,
			Section section,
			Task task,
			Submission submission,
			User student, User teacher, User parent, User staff
	) {}

	private Fixture setup() {
		Tenant tenant = createTenant("it-rbac-");
		seedAcademicCatalog(tenant);
		AcademicYear year = activateNewYear(tenant, "2026");
		Grade first = firstGradeOfPrimaria(tenant);
		Section section = createSection(tenant, year, first, "A");

		User student = createUserWithRole(tenant, "student@rbac.test", "STUDENT");
		User teacher = createUserWithRole(tenant, "teacher@rbac.test", "TEACHER");
		User parent = createUserWithRole(tenant, "parent@rbac.test", "PARENT");
		User staff = createUserWithRole(tenant, "staff@rbac.test", "STAFF");

		Task task = createTask(tenant, section, teacher);
		Submission submission = createSubmission(tenant, task, student);

		return new Fixture(tenant, section, task, submission, student, teacher, parent, staff);
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createUserWithRole(Tenant tenant, String email, String roleName) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					// Password irrelevant: we mint a JWT directly. Use any
					// well-formed hash to satisfy NOT NULL.
					user.setPasswordHash("$2a$10$dummy.hash.for.it.only.satisfies.not.null");
					user.setFirstName(roleName);
					user.setLastName(tenant.getSlug());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.valueOf(roleName));
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

	private AcademicYear activateNewYear(Tenant tenant, String name) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicYear y = new AcademicYear();
			y.setName(name);
			y.setStatus(AcademicYearStatus.ACTIVE);
			y.setStartDate(LocalDate.of(2026, 3, 1));
			y.setEndDate(LocalDate.of(2026, 12, 15));
			return yearRepository.saveAndFlush(y);
		}));
	}

	private Grade firstGradeOfPrimaria(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			return gradeRepository.findAllByLevelOrderByOrdinalAsc(primaria).get(0);
		}));
	}

	private Section createSection(Tenant tenant, AcademicYear year, Grade grade, String name) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Section section = new Section();
			section.setAcademicYear(year);
			section.setGrade(grade);
			section.setName(name);
			section.setDisplayOrder(1);
			return sectionRepository.saveAndFlush(section);
		}));
	}

	private Task createTask(Tenant tenant, Section section, User owner) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Task t = new Task();
			t.setSection(section);
			t.setTitle("task-RBAC");
			t.setDescription("desc-RBAC");
			t.setDueAt(Instant.now().plus(7, ChronoUnit.DAYS));
			t.setOwnerUserId(owner.getPublicUuid());
			t.setAllowResubmission(true);
			return taskRepository.saveAndFlush(t);
		}));
	}

	private Submission createSubmission(Tenant tenant, Task task, User student) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Submission sub = new Submission();
			sub.setTask(task);
			sub.setStudentUserId(student.getPublicUuid());
			sub.setSubmitterUserId(student.getPublicUuid());
			sub.setTextBody("body-for-RBAC");
			sub.setStatus(SubmissionStatus.SUBMITTED);
			return submissionRepository.saveAndFlush(sub);
		}));
	}
}
