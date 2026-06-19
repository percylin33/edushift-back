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
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.repository.TaskRepository;
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
 * Cross-tenant isolation IT for {@code /api/v1/.../tasks} (Sprint 7a / BE-7a.4).
 *
 * <p>Validates the same guarantees as {@code MaterialTenantIsolationIT}
 * but for the {@code lms_tasks} entity (BE-7a.2). Cross-tenant access
 * must resolve as HTTP 404 (anti-enumeration, see D-TSK-04), never
 * 403 which would leak the existence of the resource.
 */
@DisplayName("Tasks multi-tenancy isolation")
class TaskTenantIsolationIT extends IntegrationTest {

	private static final String TASKS_BASE = "/v1/tasks";
	private static final String SECTIONS_TASKS_BASE = "/v1/sections/{section}/tasks";
	private static final String AUTH_BASE = "/v1/auth";

	private static final String SHARED_EMAIL = "admin@tsk-isolation.test";
	private static final String PASSWORD_A = "PassTskA-1!";
	private static final String PASSWORD_B = "PassTskB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private TaskRepository taskRepository;
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
	@DisplayName("Cross-tenant reads (admin B looking at admin A's task)")
	class CrossTenantReads {

		@Test
		@DisplayName("GET /tasks/{A's uuid} → 404")
		void getByPublicUuidReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doGet(
					TASKS_BASE + "/" + taskA.getPublicUuid(),
					loginB.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("GET /sections/{A's section}/tasks → 200, A's task not in payload")
		void listBySectionDoesNotLeak() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);
			createTaskInB(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doGet(
					SECTIONS_TASKS_BASE.replace("{section}",
							fixture.sectionA().getPublicUuid().toString()),
					loginB.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(response.getBody());
			JsonNode content = body.isObject() ? body.get("content") : body;
			for (JsonNode item : content) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(id)
						.as("tenant A task publicUuid leaked into B's listing")
						.isNotEqualTo(taskA.getPublicUuid());
			}
		}
	}

	// =========================================================================
	// Cross-tenant writes
	// =========================================================================

	@Nested
	@DisplayName("Cross-tenant writes (admin B trying to mutate admin A's task)")
	class CrossTenantWrites {

		@Test
		@DisplayName("POST /sections/{A's section}/tasks as admin B → 404")
		void createOnAnotherTenantsSectionReturns404() throws Exception {
			Fixture fixture = setupTenants();

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			String body = String.format(
					"{\"title\":\"hijacked\",\"description\":\"hijack\","
							+ "\"dueAt\":\"%s\"}",
					Instant.now().plus(7, ChronoUnit.DAYS));

			ResponseEntity<String> response = doPost(
					SECTIONS_TASKS_BASE.replace("{section}",
							fixture.sectionA().getPublicUuid().toString()),
					loginB.accessToken(),
					body);

			assertThat(response.getStatusCode())
					.as("admin B cannot create a task in A's section; the section is invisible")
					.isEqualTo(HttpStatus.NOT_FOUND);

			List<Task> aTasks = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> taskRepository.findAll()));
			assertThat(aTasks).isEmpty();
		}

		@Test
		@DisplayName("PATCH /tasks/{A's uuid} as admin B → 404")
		void patchReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			String body = "{\"title\":\"hijacked\"}";

			ResponseEntity<String> response = doPatch(
					TASKS_BASE + "/" + taskA.getPublicUuid(),
					loginB.accessToken(),
					body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("DELETE /tasks/{A's uuid} as admin B → 404")
		void deleteReturns404() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);

			AuthResponse loginB = login(fixture.tenantB().getSlug(), SHARED_EMAIL, PASSWORD_B);
			ResponseEntity<String> response = doDelete(
					TASKS_BASE + "/" + taskA.getPublicUuid(),
					loginB.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

			Task stillAlive = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s ->
							taskRepository.findByPublicUuid(taskA.getPublicUuid()).orElseThrow()));
			assertThat(stillAlive.isDeleted()).isFalse();
		}
	}

	// =========================================================================
	// Same-tenant happy path (control)
	// =========================================================================

	@Nested
	@DisplayName("Same-tenant happy path (control)")
	class SameTenantAccess {

		@Test
		@DisplayName("Admin A creates then GETs a task in A's section → 200")
		void createThenGet() throws Exception {
			Fixture fixture = setupTenants();
			Task taskA = createTaskInA(fixture);

			AuthResponse loginA = login(fixture.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);
			ResponseEntity<String> response = doGet(
					TASKS_BASE + "/" + taskA.getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode body = objectMapper.readTree(response.getBody());
			assertThat(body.get("success").asBoolean()).isTrue();
			assertThat(body.get("data").get("publicUuid").asText())
					.isEqualTo(taskA.getPublicUuid().toString());
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

	private ResponseEntity<String> doPatch(String path, String bearer, String body) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.PATCH,
				new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> doDelete(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	// =========================================================================
	// Fixtures
	// =========================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			User adminA, User adminB,
			Section sectionA, Section sectionB
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-tsk-a-");
		Tenant tenantB = createTenant("it-tsk-b-");
		User adminA = createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		User adminB = createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		AcademicYear yearA = activateNewYear(tenantA, "2026");
		AcademicYear yearB = activateNewYear(tenantB, "2026");

		Grade firstA = firstGradeOfPrimaria(tenantA);
		Grade firstB = firstGradeOfPrimaria(tenantB);

		Section sectionA = createSection(tenantA, yearA, firstA, "A");
		Section sectionB = createSection(tenantB, yearB, firstB, "A");

		return new Fixture(tenantA, tenantB, adminA, adminB, sectionA, sectionB);
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
		return createTask(fixture.tenantA(), fixture.sectionA(), fixture.adminA(), "task-A");
	}

	private Task createTaskInB(Fixture fixture) {
		return createTask(fixture.tenantB(), fixture.sectionB(), fixture.adminB(), "task-B");
	}

	private Task createTask(Tenant tenant, Section section, User owner, String title) {
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
}
