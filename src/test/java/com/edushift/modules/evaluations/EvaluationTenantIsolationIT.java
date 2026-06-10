package com.edushift.modules.evaluations;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-tenant isolation IT for
 * {@code /v1/academic/assignments/{uuid}/evaluations} and
 * {@code /v1/academic/evaluations/{publicUuid}} (Sprint 5B / BE-5B.1).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>Read isolation</strong> — admin A only sees A's evaluations;
 *       B's never appear in the listing.</li>
 *   <li><strong>Cross-tenant access</strong> — GET / PUT / DELETE / publish
 *       on B's evaluation from A → 404.</li>
 *   <li><strong>Name uniqueness is per-assignment</strong> — both tenants
 *       can have a "Tarea 1" inside their own assignment.</li>
 *   <li><strong>Cross-tenant assignment reference</strong> — POST
 *       referencing B's assignment UUID from A → 404
 *       (anti-enumeration).</li>
 *   <li><strong>Lifecycle isolation</strong> — publish / close against
 *       B's evaluation from A → 404.</li>
 * </ul>
 */
@DisplayName("Evaluations multi-tenancy isolation")
class EvaluationTenantIsolationIT extends IntegrationTest {

	private static final String EVAL_BY_ASSIGNMENT_BASE = "/v1/academic/assignments";
	private static final String EVAL_FLAT_BASE = "/v1/academic/evaluations";
	private static final String AUTH_BASE = "/v1/auth";
	private static final String SHARED_EMAIL = "shared-eval@isolation.test";
	private static final String PASSWORD_A = "PassEvalA-1!";
	private static final String PASSWORD_B = "PassEvalB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicPeriodRepository periodRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private CourseRepository courseRepository;
	@Autowired private CourseLevelRepository courseLevelRepository;
	@Autowired private TeacherRepository teacherRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private UnitRepository unitRepository;
	@Autowired private EvaluationRepository evaluationRepository;
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
	// Read isolation
	// =========================================================================

	@Nested
	@DisplayName("Read isolation")
	class Read {

		@Test
		@DisplayName("admin A only sees A's evaluations in the assignment listing")
		void listIsScoped() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					EVAL_BY_ASSIGNMENT_BASE + "/" + fx.assignmentA().getPublicUuid()
							+ "/evaluations",
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode array = objectMapper.readTree(response.getBody());

			List<Evaluation> bEvals = TenantContext.runAs(fx.tenantB().getId(),
					() -> tx().execute(s -> evaluationRepository.findAll()));
			List<UUID> bIds = bEvals.stream()
					.map(Evaluation::getPublicUuid).toList();

			assertThat(array).hasSizeGreaterThan(0);
			for (JsonNode item : array) {
				UUID id = UUID.fromString(item.get("publicUuid").asText());
				assertThat(bIds)
						.as("tenant B evaluation publicUuid leaked into A's response")
						.doesNotContain(id);
			}
		}

		@Test
		@DisplayName("admin A reading B's evaluation by id → 404")
		void crossTenantGetIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					EVAL_FLAT_BASE + "/" + fx.evaluationB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("admin A listing evaluations of B's assignment → 404")
		void crossTenantAssignmentIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doGet(
					EVAL_BY_ASSIGNMENT_BASE + "/" + fx.assignmentB().getPublicUuid()
							+ "/evaluations",
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
		@DisplayName("Same evaluation NAME is allowed in two tenants (per-assignment uniqueness)")
		void nameUniquenessIsPerAssignment() {
			Fixture fx = setupTenants();
			long countA = TenantContext.runAs(fx.tenantA().getId(),
					() -> tx().execute(s -> evaluationRepository.count()));
			long countB = TenantContext.runAs(fx.tenantB().getId(),
					() -> tx().execute(s -> evaluationRepository.count()));

			assertThat(countA).isGreaterThan(0);
			assertThat(countB).isGreaterThan(0);
		}

		@Test
		@DisplayName("Cross-tenant POST with B's assignment → 404 RESOURCE_NOT_FOUND")
		void crossTenantPostAssignmentIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = "{"
					+ "\"kind\":\"TASK\","
					+ "\"name\":\"Tarea Hacker\","
					+ "\"weight\":1.00,"
					+ "\"scheduledDate\":\"2026-05-01\","
					+ "\"scale\":\"SCORE_0_20\""
					+ "}";

			ResponseEntity<String> response = doPost(
					EVAL_BY_ASSIGNMENT_BASE + "/" + fx.assignmentB().getPublicUuid()
							+ "/evaluations",
					loginA.accessToken(), body);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant DELETE → 404")
		void crossTenantDeleteIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doDelete(
					EVAL_FLAT_BASE + "/" + fx.evaluationB().getPublicUuid(),
					loginA.accessToken());

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant PUT → 404")
		void crossTenantPutIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			String body = "{\"description\":\"hijack\"}";
			ResponseEntity<String> response = rest.exchange(
					EVAL_FLAT_BASE + "/" + fx.evaluationB().getPublicUuid(),
					HttpMethod.PUT,
					new HttpEntity<>(body, jsonHeadersWithAuth(loginA.accessToken())),
					String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant /publish → 404")
		void crossTenantPublishIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doPost(
					EVAL_FLAT_BASE + "/" + fx.evaluationB().getPublicUuid() + "/publish",
					loginA.accessToken(), "{}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("Cross-tenant /close → 404")
		void crossTenantCloseIs404() throws Exception {
			Fixture fx = setupTenants();
			AuthResponse loginA = login(fx.tenantA().getSlug(), SHARED_EMAIL, PASSWORD_A);

			ResponseEntity<String> response = doPost(
					EVAL_FLAT_BASE + "/" + fx.evaluationB().getPublicUuid() + "/close",
					loginA.accessToken(), "{}");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

	private HttpHeaders jsonHeadersWithAuth(String bearer) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(bearer);
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

	private ResponseEntity<String> doDelete(String path, String bearer) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(bearer);
		return rest.exchange(path, HttpMethod.DELETE,
				new HttpEntity<>(headers), String.class);
	}

	// =========================================================================
	// Fixture
	// =========================================================================

	record Fixture(
			Tenant tenantA, Tenant tenantB,
			TeacherAssignment assignmentA, TeacherAssignment assignmentB,
			Evaluation evaluationA, Evaluation evaluationB
	) {}

	private Fixture setupTenants() {
		Tenant tenantA = createTenant("it-eval-a-");
		Tenant tenantB = createTenant("it-eval-b-");
		createAdmin(tenantA, SHARED_EMAIL, PASSWORD_A);
		createAdmin(tenantB, SHARED_EMAIL, PASSWORD_B);
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		Bundle bundleA = seedEvaluation(tenantA);
		Bundle bundleB = seedEvaluation(tenantB);

		return new Fixture(tenantA, tenantB,
				bundleA.assignment(), bundleB.assignment(),
				bundleA.evaluation(), bundleB.evaluation());
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

	private void seedAcademicCatalog(Tenant tenant) {
		TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					seedService.seedDefaults(tenant.getId());
					return null;
				}));
	}

	record Bundle(TeacherAssignment assignment, Evaluation evaluation) {}

	private Bundle seedEvaluation(Tenant tenant) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			AcademicLevel primaria = levelRepository.findByCodeIgnoreCase("PRIMARIA")
					.orElseThrow();
			Grade grade = gradeRepository
					.findAllByLevelOrderByOrdinalAsc(primaria).get(0);

			AcademicYear year = new AcademicYear();
			year.setName("2026-IT-EVAL");
			year.setStartDate(LocalDate.of(2026, 3, 1));
			year.setEndDate(LocalDate.of(2026, 12, 20));
			year.setStatus(AcademicYearStatus.ACTIVE);
			AcademicYear savedYear = yearRepository.saveAndFlush(year);

			AcademicPeriod period = new AcademicPeriod();
			period.setAcademicYear(savedYear);
			period.setPeriodType(PeriodType.BIMESTRE);
			period.setOrdinal(1);
			period.setName("I Bimestre");
			period.setStartDate(LocalDate.of(2026, 3, 1));
			period.setEndDate(LocalDate.of(2026, 5, 31));
			AcademicPeriod savedPeriod = periodRepository.saveAndFlush(period);

			Section section = new Section();
			section.setAcademicYear(savedYear);
			section.setGrade(grade);
			section.setName("1ro A");
			Section savedSection = sectionRepository.saveAndFlush(section);

			Course course = new Course();
			course.setCode("MAT-IT-E");
			course.setName("Matemática IT Evals");
			course.setIsActive(true);
			Course savedCourse = courseRepository.saveAndFlush(course);

			CourseLevel link = new CourseLevel();
			link.setCourse(savedCourse);
			link.setLevel(primaria);
			courseLevelRepository.saveAndFlush(link);

			Teacher teacher = new Teacher();
			teacher.setFirstName("María");
			teacher.setLastName("García");
			teacher.setDocumentType(DocumentType.DNI);
			teacher.setDocumentNumber("87654321" + tenant.getSlug().substring(0, 1));
			teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);
			Teacher savedTeacher = teacherRepository.saveAndFlush(teacher);

			TeacherAssignment assignment = new TeacherAssignment();
			assignment.setTeacher(savedTeacher);
			assignment.setSection(savedSection);
			assignment.setCourse(savedCourse);
			assignment.setAcademicPeriod(savedPeriod);
			assignment.setAssignedAt(Instant.now());
			TeacherAssignment savedAssignment = assignmentRepository
					.saveAndFlush(assignment);

			// We seed a unit so the anchor paths are exercised, but we
			// don't anchor the evaluation to it (BE-5B.1 supports
			// optional anchors; the test focuses on tenant isolation).
			Unit unit = new Unit();
			unit.setCourse(savedCourse);
			unit.setName("Unidad I IT");
			unit.setDisplayOrder(1);
			unit.setIsActive(true);
			unitRepository.saveAndFlush(unit);

			Evaluation evaluation = new Evaluation();
			evaluation.setTeacherAssignment(savedAssignment);
			evaluation.setKind(EvaluationKind.TASK);
			evaluation.setName("Tarea 1");
			evaluation.setWeight(BigDecimal.valueOf(1.00));
			evaluation.setScheduledDate(LocalDate.of(2026, 4, 10));
			evaluation.setScale(EvaluationScale.SCORE_0_20);
			evaluation.setStatus(EvaluationStatus.DRAFT);
			evaluation.setIsActive(Boolean.TRUE);
			Evaluation savedEvaluation = evaluationRepository
					.saveAndFlush(evaluation);

			return new Bundle(savedAssignment, savedEvaluation);
		}));
	}
}
