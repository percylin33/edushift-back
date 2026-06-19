package com.edushift.modules.tasks.submission;

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
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.edushift.modules.tasks.submission.repository.SubmissionRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Cross-tenant isolation IT for {@code /v1/.../submissions} (Sprint 7a / BE-7a.4).
 *
 * <p>Validates that LMS submissions (BE-7a.2) inherit the same tenant
 * isolation guarantees: a user authenticated in tenant B must never be
 * able to read, grade, or list a submission owned by tenant A. The
 * expected outcome of every cross-tenant request is HTTP 404, never
 * 403 (anti-enumeration contract, see D-TSK-04).
 *
 * <h3>Known v1 limitations (out of scope here)</h3>
 * The following in-tenant authorisation checks are documented as
 * follow-up tech-debt items and intentionally NOT covered by this
 * tenant-isolation suite:
 * <ul>
 *   <li>DEBT-7A-23: parent-link check (parent on behalf of student)</li>
 *   <li>DEBT-7A-24: section-enrollment check (student in section)</li>
 *   <li>DEBT-7A-25: teacher-must-own-section check (grading)</li>
 * </ul>
 */
@DisplayName("Submissions multi-tenancy isolation")
class SubmissionTenantIsolationIT extends IntegrationTest {

	private static final String TASKS_SUBS_BASE = "/v1/tasks/{task}/submissions";
	private static final String SUBMISSIONS_BASE = "/v1/submissions";
	private static final String AUTH_BASE = "/v1/auth";

	private static final String ADMIN_EMAIL = "admin@sub-isolation.test";
	private static final String ADMIN_PASSWORD_A = "PassSubA-1!";
	private static final String ADMIN_PASSWORD_B = "PassSubB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private TaskRepository taskRepository;
	@Autowired private SubmissionRepository submissionRepository;
	@Autowired private AcademicSeedService seedService;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Cross-tenant reads
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant reads (admin B looking at admin A's submission)")
	class CrossTenantReads {

		@Test
		@DisplayName("GET /tasks/{A's task}/submissions as admin B → 200, A's submission not in payload")
		void listByTaskDoesNotLeak() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);
			Submission submissionA = createSubmissionInA(fixture, taskA);
			Task taskB = createTaskInB(fixture);
			createSubmissionInB(fixture, taskB);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD_B);
			ResponseEntity<String> response = doGet(
					TASKS_SUBS_BASE.replace("{task}", taskA.getPublicUuid().toString()),
					loginB.accessToken());

			// The task itself is invisible to tenant B, so the list resolves as 404.
			assertThat(response.getStatusCode())
					.as("admin B listing submissions of a task owned by A must be 404")
					.isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(response.getBody()).doesNotContain(submissionA.getPublicUuid().toString());
		}

		@Test
		@DisplayName("GET /tasks/{A's task}/submissions/me as admin B → 404 (task invisible)")
		void getMineReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);
			createSubmissionInA(fixture, taskA);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD_B);
			ResponseEntity<String> response = doGet(
					TASKS_SUBS_BASE.replace("{task}", taskA.getPublicUuid().toString()) + "/me",
					loginB.accessToken());

			assertThat(response.getStatusCode())
					.as("admin B's GET /me on A's task is 404; the task is invisible across tenants")
					.isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// Cross-tenant writes
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant writes (admin B trying to mutate admin A's submission)")
	class CrossTenantWrites {

		@Test
		@DisplayName("POST /tasks/{A's task}/submissions as admin B → 404 (task invisible)")
		void submitOnAnotherTenantsTaskReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);
			UUID studentA = fixture.studentA().getPublicUuid();

			AuthResponse loginB = login(fixture.tenantB().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD_B);
			String body = String.format(
					"{\"studentPublicUuid\":\"%s\",\"textBody\":\"hijack\"}",
					studentA);

			ResponseEntity<String> response = doPost(
					TASKS_SUBS_BASE.replace("{task}", taskA.getPublicUuid().toString()),
					loginB.accessToken(),
					body);

			assertThat(response.getStatusCode())
					.as("admin B cannot submit on A's task; the task is invisible")
					.isEqualTo(HttpStatus.NOT_FOUND);

			List<Submission> aSubs = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> submissionRepository.findAll()));
			assertThat(aSubs).isEmpty();
		}

		@Test
		@DisplayName("PATCH /submissions/{A's uuid}/grade as admin B → 404")
		void gradeReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);
			Submission submissionA = createSubmissionInA(fixture, taskA);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD_B);
			String body = "{\"grade\":99,\"feedback\":\"hijack\"}";

			ResponseEntity<String> response = doPatch(
					SUBMISSIONS_BASE + "/" + submissionA.getPublicUuid() + "/grade",
					loginB.accessToken(),
					body);

			assertThat(response.getStatusCode())
					.as("admin B grading A's submission must be 404 (anti-enumeration)")
					.isEqualTo(HttpStatus.NOT_FOUND);

			// Defence-in-depth: the submission is still SUBMITTED (not GRADED).
			Submission stillAlive = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> submissionRepository
							.findByPublicUuid(submissionA.getPublicUuid()).orElseThrow()));
			assertThat(stillAlive.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
		}
	}

	// =========================================================================
	// Same-tenant happy path (control)
	// =========================================================================

	@Nested
	@DisplayName("Same-tenant happy path (control)")
	class SameTenantAccess {

		@Test
		@DisplayName("Student A submits then admin A lists → 201 then 200 includes the row")
		void studentSubmitsThenAdminLists() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);
			UUID studentAUuid = fixture.studentA().getPublicUuid();

			// 1. Student A submits.
			AuthResponse studentA = loginAsStudent(fixture.tenantA(), fixture.studentA());
			String submitBody = String.format(
					"{\"studentPublicUuid\":\"%s\",\"textBody\":\"my answer\"}",
					studentAUuid);
			ResponseEntity<String> submit = doPost(
					TASKS_SUBS_BASE.replace("{task}", taskA.getPublicUuid().toString()),
					studentA.accessToken(),
					submitBody);
			assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);

			// 2. Admin A lists — sees the submission.
			AuthResponse adminA = login(fixture.tenantA().getSlug(), ADMIN_EMAIL, ADMIN_PASSWORD_A);
			ResponseEntity<String> list = doGet(
					TASKS_SUBS_BASE.replace("{task}", taskA.getPublicUuid().toString()),
					adminA.accessToken());
			assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(list.getBody());
			JsonNode content = body.isObject() ? body.get("content") : body;
			assertThat(content).isNotEmpty();
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

	private AuthResponse loginAsStudent(Tenant tenant, User student) throws Exception {
		return login(tenant.getSlug(), student.getEmail(), "PassStd-1!");
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
			Tenant tenantA, Tenant tenantB,
			Section sectionA, Section sectionB,
			User adminA, User adminB, User studentA
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-sub-a-");
		Tenant tenantB = createTenant("it-sub-b-");
		User adminA = createAdmin(tenantA, ADMIN_EMAIL, ADMIN_PASSWORD_A);
		User adminB = createAdmin(tenantB, ADMIN_EMAIL, ADMIN_PASSWORD_B);
		User studentA = createStudent(tenantA, "student-a@sub-isolation.test", "PassStd-1!");

		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		AcademicYear yearA = activateNewYear(tenantA, "2026");
		AcademicYear yearB = activateNewYear(tenantB, "2026");

		Grade firstA = firstGradeOfPrimaria(tenantA);
		Grade firstB = firstGradeOfPrimaria(tenantB);

		Section sectionA = createSection(tenantA, yearA, firstA, "A");
		Section sectionB = createSection(tenantB, yearB, firstB, "A");

		return new Fixture(tenantA, tenantB, sectionA, sectionB, adminA, adminB, studentA);
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

	private User createStudent(Tenant tenant, String email, String rawPassword) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
					user.setPasswordHash(passwordEncoder.encode(rawPassword));
					user.setFirstName("Std");
					user.setLastName(tenant.getSlug());
					user.setStatus(UserStatus.ACTIVE);
					user.setEmailVerified(true);
					user.setMfaEnabled(false);
					user.addRole(UserRole.STUDENT);
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

	private Task createTaskInA(Fixture fixture) {
		return createTask(fixture.tenantA(), fixture.sectionA(), "task-A", fixture.adminA());
	}

	private Task createTaskInB(Fixture fixture) {
		return createTask(fixture.tenantB(), fixture.sectionB(), "task-B", fixture.adminB());
	}

	private Task createTask(Tenant tenant, Section section, String title, User owner) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Task t = new Task();
			t.setSection(section);
			t.setTitle(title);
			t.setDescription("desc-" + title);
			t.setDueAt(Instant.now().plus(7, ChronoUnit.DAYS));
			t.setOwnerUserId(owner.getPublicUuid());
			t.setAllowResubmission(true);
			return taskRepository.saveAndFlush(t);
		}));
	}

	private Submission createSubmissionInA(Fixture fixture, Task task) {
		return createSubmission(fixture.tenantA(), task, fixture.studentA().getPublicUuid());
	}

	private Submission createSubmissionInB(Fixture fixture, Task task) {
		// Use fixture.adminB() (a real User in tenant B) as the
		// "student" for the cross-tenant submission row. Using
		// UUID.randomUUID() would violate fk_lms_submissions_student
		// because the column is a soft FK to users.public_uuid.
		return createSubmission(fixture.tenantB(), task, fixture.adminB().getPublicUuid());
	}

	private Submission createSubmission(Tenant tenant, Task task, UUID studentUuid) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Submission sub = new Submission();
			sub.setTask(task);
			sub.setStudentUserId(studentUuid);
			sub.setSubmitterUserId(studentUuid);
			sub.setTextBody("body-" + studentUuid);
			sub.setStatus(SubmissionStatus.SUBMITTED);
			return submissionRepository.saveAndFlush(sub);
		}));
	}
}
