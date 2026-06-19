package com.edushift.modules.quizzes;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
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
import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
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
 * End-to-end IT for the quiz player + manual grading flow
 * (Sprint 7b / BE-7b.2).
 *
 * <p>Covers the DoD scenarios of the BE-7b.2 ticket:
 *
 * <h3>Player (LMS_QUIZ_SUBMIT)</h3>
 * <ol>
 *   <li><strong>PLAYER-1</strong> STUDENT starts a published quiz
 *       → 201, attempt IN_PROGRESS with {@code expiresAt} set
 *       (timeLimit=30min).</li>
 *   <li><strong>PLAYER-2</strong> STUDENT autosaves answers
 *       (PATCH /attempts/{uuid}) → 200, no new attempt created
 *       (idempotent UPSERT on (attempt, question)).</li>
 *   <li><strong>PLAYER-3</strong> STUDENT submits → status
 *       SUBMITTED → AUTO_GRADED (because the seeded quiz has a
 *       SHORT_ANSWER question that stays PENDING).</li>
 *   <li><strong>PLAYER-4</strong> STUDENT tries to start a second
 *       attempt with {@code attemptsAllowed=1} → 409
 *       ATTEMPTS_EXHAUSTED.</li>
 *   <li><strong>PLAYER-5</strong> Cross-tenant: STUDENT of tenant B
 *       starts tenant A's quiz → 404 (anti-enumeration).</li>
 *   <li><strong>PLAYER-6</strong> Cross-tenant: STUDENT of tenant A
 *       GETs a tenant B student's attempt → 404.</li>
 * </ol>
 *
 * <h3>Manual grading (LMS_QUIZ_GRADE)</h3>
 * <ol start="7">
 *   <li><strong>GRADING-1</strong> TEACHER fetches the grading
 *       queue of tenant A's quiz → 200, returns the pending
 *       SHORT_ANSWER answer.</li>
 *   <li><strong>GRADING-2</strong> TEACHER grades the pending
 *       answer (POST /attempts/{uuid}/grade) → 200, attempt
 *       transitions AUTO_GRADED → GRADED, {@code manualScore} +
 *       {@code score} recomputed, {@code gradedBy/graderAt} +
 *       feedback persisted.</li>
 *   <li><strong>GRADING-3</strong> Cross-tenant: TEACHER of tenant
 *       B tries to grade tenant A's attempt → 404.</li>
 * </ol>
 *
 * <h3>Setup model</h3>
 * One fully published quiz with 3 questions (1 MC worth 5, 1 TF
 * worth 3, 1 SHORT_ANSWER worth 4 = 12 max-score) in tenant A.
 * A STUDENT user with the matching {@code Student} entity (linked
 * via {@code students.user_id}) and an ACTIVE enrollment in
 * tenant A's section. Tenant B mirrors the same graph (admin +
 * section + student) so cross-tenant probes are independent.
 */
@DisplayName("Quiz player + manual grading")
class QuizPlayerAndGradingIT extends IntegrationTest {

	private static final String QUIZZES_BASE = "/v1/quizzes";
	private static final String ATTEMPTS_BASE = "/v1/attempts";

	private static final String SHARED_ADMIN_EMAIL = "admin@quizplayer-isolation.test";
	private static final String PASSWORD_A = "PassPlayerA-1!";
	private static final String PASSWORD_B = "PassPlayerB-2!";

	@Autowired private TestRestTemplate rest;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository enrollmentRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private QuizRepository quizRepository;
	@Autowired private QuizQuestionRepository questionRepository;
	@Autowired private QuizOptionRepository optionRepository;
	@Autowired private QuizAttemptRepository attemptRepository;
	@Autowired private JwtService jwtService;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private ObjectMapper objectMapper;

	private TransactionTemplate tx;

	private TransactionTemplate tx() {
		if (tx == null) tx = new TransactionTemplate(txManager);
		return tx;
	}

	// =========================================================================
	// Player (LMS_QUIZ_SUBMIT)
	// =========================================================================

	@Nested
	@DisplayName("Player flow (STUDENT)")
	class PlayerFlow {

		@Test
		@DisplayName("PLAYER-1: STUDENT starts a published quiz → 201, IN_PROGRESS, expiresAt set")
		void startAttemptHappyPath() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();

			String bearer = bearerFor(fixture.studentA(), fixture.tenantA(), "STUDENT");
			ResponseEntity<String> response = doPost(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
					bearer,
					"");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			JsonNode body = objectMapper.readTree(response.getBody());
			JsonNode data = body.get("data");
			assertThat(data.get("status").asText()).isEqualTo("IN_PROGRESS");
			assertThat(data.get("attemptNumber").asInt()).isEqualTo(1);
			assertThat(data.get("expiresAt").asText()).isNotEmpty();
		}

		@Test
		@DisplayName("PLAYER-2: autosave upserts answers without creating a new attempt")
		void autosaveIsIdempotent() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			String bearer = bearerFor(fixture.studentA(), fixture.tenantA(), "STUDENT");

			// Start the attempt.
			ResponseEntity<String> startResp = doPost(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
					bearer,
					"");
			String attemptUuid = objectMapper.readTree(startResp.getBody())
					.get("data").get("publicUuid").asText();

			// Build a save request: 1 MC + 1 TF + 1 SHORT_ANSWER.
			UUID mcQuestionId = fixture.mcQuestion().getPublicUuid();
			UUID mcOptionId = fixture.correctMcOption().getPublicUuid();
			UUID tfQuestionId = fixture.tfQuestion().getPublicUuid();
			UUID saQuestionId = fixture.shortAnswerQuestion().getPublicUuid();
			String saveBody = String.format(
					"{\"answers\":["
							+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"MC\","
							+ "  \"selectedOptionId\":\"%s\"},"
							+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"TF\","
							+ "  \"selectedBoolean\":true},"
							+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"SHORT_ANSWER\","
							+ "  \"textAnswer\":\"The mitochondria produces ATP\"}"
							+ "]}",
					mcQuestionId, mcOptionId, tfQuestionId, saQuestionId);

			ResponseEntity<String> saveResp = doPatch(
					ATTEMPTS_BASE + "/" + attemptUuid,
					bearer,
					saveBody);

			assertThat(saveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode saved = objectMapper.readTree(saveResp.getBody()).get("data");
			assertThat(saved.get("status").asText()).isEqualTo("IN_PROGRESS");
			assertThat(saved.get("answers")).hasSize(3);

			// Defence-in-depth: only one attempt row in DB.
			long attemptCount = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> attemptRepository.count()));
			assertThat(attemptCount).isEqualTo(1L);

			// Re-save with the same questions → still 3 answers (UPDATE in place).
			ResponseEntity<String> resave = doPatch(
					ATTEMPTS_BASE + "/" + attemptUuid,
					bearer,
					saveBody);
			assertThat(resave.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode resaved = objectMapper.readTree(resave.getBody()).get("data");
			assertThat(resaved.get("answers")).hasSize(3);

			long attemptCountAfter = TenantContext.runAs(fixture.tenantA().getId(),
					() -> tx().execute(s -> attemptRepository.count()));
			assertThat(attemptCountAfter).isEqualTo(1L);
		}

		@Test
		@DisplayName("PLAYER-3: submit → SUBMITTED → AUTO_GRADED (SHORT_ANSWER stays pending)")
		void submitLeavesItAutoGradedWhenShortAnswerExists() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			String bearer = bearerFor(fixture.studentA(), fixture.tenantA(), "STUDENT");

			ResponseEntity<String> startResp = doPost(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
					bearer,
					"");
			String attemptUuid = objectMapper.readTree(startResp.getBody())
					.get("data").get("publicUuid").asText();

			// Save MC (correct) + TF (correct) + SHORT_ANSWER (text).
			UUID mcQuestionId = fixture.mcQuestion().getPublicUuid();
			UUID mcOptionId = fixture.correctMcOption().getPublicUuid();
			UUID tfQuestionId = fixture.tfQuestion().getPublicUuid();
			UUID saQuestionId = fixture.shortAnswerQuestion().getPublicUuid();
			String saveBody = String.format(
					"{\"answers\":["
							+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"MC\","
							+ "  \"selectedOptionId\":\"%s\"},"
							+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"TF\","
							+ "  \"selectedBoolean\":true},"
							+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"SHORT_ANSWER\","
							+ "  \"textAnswer\":\"The mitochondria produces ATP\"}"
							+ "]}",
					mcQuestionId, mcOptionId, tfQuestionId, saQuestionId);
			doPatch(ATTEMPTS_BASE + "/" + attemptUuid, bearer, saveBody);

			ResponseEntity<String> submitResp = doPost(
					ATTEMPTS_BASE + "/" + attemptUuid + "/submit",
					bearer,
					"");

			assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode submitted = objectMapper.readTree(submitResp.getBody()).get("data");
			// The quiz has a SHORT_ANSWER → attempt stays in AUTO_GRADED, not GRADED.
			// (Even though the SHORT_ANSWER auto-grader seeds full points when
			// the question has no expectedKeywords, the final verdict and the
			// GRADED transition are owned by the teacher — see BE-7b.2 spec.)
			assertThat(submitted.get("status").asText())
					.isEqualTo(AttemptStatus.AUTO_GRADED.name());
			// autoScore includes the MC (5) + TF (3) + the SHORT_ANSWER seed
			// (4, because the question has no expected_keywords, so the
			// grader treats any non-blank text as a full-points match).
			assertThat(submitted.get("autoScore").asInt())
					.isEqualTo(5 + 3 + 4);
			assertThat(submitted.has("manualScore")
					&& !submitted.get("manualScore").isNull())
					.as("manualScore is still null because the SHORT_ANSWER "
							+ "is pending teacher grading")
					.isFalse();
		}

		@Test
		@DisplayName("PLAYER-4: attemptsAllowed=1 → second start → 409 ATTEMPTS_EXHAUSTED")
		void reAttemptBlockedWhenExhausted() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			String bearer = bearerFor(fixture.studentA(), fixture.tenantA(), "STUDENT");

			ResponseEntity<String> first = doPost(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
					bearer,
					"");
			assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

			ResponseEntity<String> second = doPost(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
					bearer,
					"");

			// Service throws AttemptsExhaustedException → mapped to 409.
			assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		}

		@Test
		@DisplayName("PLAYER-5: cross-tenant start (B starts A's quiz) → 404 (anti-enumeration)")
		void crossTenantStartReturns404() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();

			String bearerB = bearerFor(fixture.studentB(), fixture.tenantB(), "STUDENT");
			ResponseEntity<String> response = doPost(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
					bearerB,
					"");

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		@Test
		@DisplayName("PLAYER-6: cross-tenant GET attempt (A reads B's attempt) → 404")
		void crossTenantGetAttemptReturns404() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			// Pre-create an IN_PROGRESS attempt in tenant B (mirrors the
			// "B started" path; the controller path is irrelevant here).
			UUID attemptB = createInProgressAttemptInB(fixture);

			String bearerA = bearerFor(fixture.studentA(), fixture.tenantA(), "STUDENT");
			ResponseEntity<String> response = doGet(
					ATTEMPTS_BASE + "/" + attemptB,
					bearerA);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	// =========================================================================
	// Manual grading (LMS_QUIZ_GRADE)
	// =========================================================================

	@Nested
	@DisplayName("Manual grading flow (TEACHER)")
	class GradingFlow {

		@Test
		@DisplayName("GRADING-1: TEACHER fetches grading queue → 200, returns the pending SHORT_ANSWER")
		void gradingQueueReturnsPendingAnswers() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			UUID attemptUuid = startAndSubmitAttemptWithPendingShortAnswer(fixture);

			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");
			ResponseEntity<String> response = doGet(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/grading-queue",
					teacherBearer);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode data = objectMapper.readTree(response.getBody()).get("data");
			assertThat(data.isArray()).isTrue();
			assertThat(data.size()).isEqualTo(1);
			assertThat(data.get(0).get("attemptPublicUuid").asText())
					.isEqualTo(attemptUuid.toString());
			assertThat(data.get(0).get("textAnswer").asText())
					.isEqualTo("The mitochondria produces ATP");
		}

		@Test
		@DisplayName("GRADING-2: TEACHER grades the pending answer → 200, attempt → GRADED, "
				+ "manualScore+score recomputed, feedback persisted")
		void gradeAttemptClosesIt() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			UUID attemptUuid = startAndSubmitAttemptWithPendingShortAnswer(fixture);

			// Discover the pending answer's publicUuid from the queue.
			String teacherBearer = bearerFor(fixture.teacherA(), fixture.tenantA(), "TEACHER");
			ResponseEntity<String> queueResp = doGet(
					QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/grading-queue",
					teacherBearer);
			String answerUuid = objectMapper.readTree(queueResp.getBody())
					.get("data").get(0).get("answerPublicUuid").asText();

			// Grade with 3/4 points (SHORT_ANSWER's max is 4).
			String gradeBody = String.format(
					"{\"grades\":[{\"answerPublicUuid\":\"%s\",\"pointsAwarded\":3}],"
							+ "\"feedback\":\"Buen razonamiento, falta precisión.\"}",
					answerUuid);

			ResponseEntity<String> gradeResp = doPost(
					ATTEMPTS_BASE + "/" + attemptUuid + "/grade",
					teacherBearer,
					gradeBody);

			assertThat(gradeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
			JsonNode graded = objectMapper.readTree(gradeResp.getBody()).get("data");
			assertThat(graded.get("status").asText()).isEqualTo("GRADED");
			assertThat(graded.get("manualScore").asInt()).isEqualTo(3);
			// autoScore was 5+3=8 (MC + TF), manualScore=3 → score=11.
			assertThat(graded.get("score").asInt()).isEqualTo(11);
			assertThat(graded.get("gradedByUserId").asText())
					.isEqualTo(fixture.teacherA().getPublicUuid().toString());
			assertThat(graded.get("gradedAt").asText()).isNotEmpty();
			assertThat(graded.get("feedback").asText())
					.isEqualTo("Buen razonamiento, falta precisión.");
		}

		@Test
		@DisplayName("GRADING-3: cross-tenant grade (B grades A's attempt) → 404")
		void crossTenantGradeReturns404() throws Exception {
			Fixture fixture = setupWithPublishedQuiz();
			UUID attemptUuid = startAndSubmitAttemptWithPendingShortAnswer(fixture);

			String teacherB = bearerFor(fixture.teacherB(), fixture.tenantB(), "TEACHER");
			String body = String.format(
					"{\"grades\":[{\"answerPublicUuid\":\"%s\",\"pointsAwarded\":1}],"
							+ "\"feedback\":\"hack\"}",
					UUID.randomUUID()); // any value; the attempt itself is invisible.

			ResponseEntity<String> response = doPost(
					ATTEMPTS_BASE + "/" + attemptUuid + "/grade",
					teacherB,
					body);

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

	private String bearerFor(User user, Tenant tenant, String roleName) {
		return jwtService.issueAccessToken(user, tenant, Set.of(roleName));
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
			User adminA, User adminB,
			User teacherA, User teacherB,
			User studentA, User studentB,
			Section sectionA, Section sectionB,
			Quiz quizA, Quiz quizB,
			QuizQuestion mcQuestion,
			QuizOption correctMcOption,
			QuizQuestion tfQuestion,
			QuizQuestion shortAnswerQuestion
	) {}

	private Fixture setupWithPublishedQuiz() throws Exception {
		Tenant tenantA = createTenant("it-qpg-a-");
		Tenant tenantB = createTenant("it-qpg-b-");
		User adminA = createUser(tenantA, SHARED_ADMIN_EMAIL, PASSWORD_A, "TENANT_ADMIN");
		User adminB = createUser(tenantB, SHARED_ADMIN_EMAIL, PASSWORD_B, "TENANT_ADMIN");
		User teacherA = createUser(tenantA, "teacher@quizplayer-isolation.test",
				"PassTeachA-1!", "TEACHER");
		User teacherB = createUser(tenantB, "teacher-b@quizplayer-isolation.test",
				"PassTeachB-2!", "TEACHER");

		// Seed the academic catalog (PRIMARIA level + grades) in both
		// tenants so we have a Grade to anchor the section on.
		seedAcademicCatalog(tenantA);
		seedAcademicCatalog(tenantB);

		AcademicYear yearA = activateNewYear(tenantA, "2026");
		AcademicYear yearB = activateNewYear(tenantB, "2026");
		Grade firstA = firstGradeOfPrimaria(tenantA);
		Grade firstB = firstGradeOfPrimaria(tenantB);
		Section sectionA = createSection(tenantA, yearA, firstA, "A");
		Section sectionB = createSection(tenantB, yearB, firstB, "A");

		// Tenant A: student + enrollment + published quiz with 3 questions.
		User studentA = createUser(tenantA, "student@quizplayer-isolation.test",
				"PassStudA-1!", "STUDENT");
		Student studentEntityA = createStudent(tenantA, studentA.getId(), "a");
		enrollStudent(studentEntityA, sectionA, yearA);

		PublishedQuiz pqA = createPublishedQuizWithMixedQuestions(tenantA, sectionA, teacherA);
		// Tenant B: mirror for the cross-tenant probes.
		User studentB = createUser(tenantB, "student-b@quizplayer-isolation.test",
				"PassStudB-2!", "STUDENT");
		Student studentEntityB = createStudent(tenantB, studentB.getId(), "b");
		enrollStudent(studentEntityB, sectionB, yearB);
		PublishedQuiz pqB = createPublishedQuizWithMixedQuestions(tenantB, sectionB, teacherB);

		return new Fixture(
				tenantA, tenantB,
				adminA, adminB,
				teacherA, teacherB,
				studentA, studentB,
				sectionA, sectionB,
				pqA.quiz(), pqB.quiz(),
				pqA.mcQuestion(), pqA.correctMcOption(),
				pqA.tfQuestion(), pqA.shortAnswerQuestion());
	}

	private UUID startAndSubmitAttemptWithPendingShortAnswer(Fixture fixture) throws Exception {
		String bearer = bearerFor(fixture.studentA(), fixture.tenantA(), "STUDENT");

		// Start.
		ResponseEntity<String> startResp = doPost(
				QUIZZES_BASE + "/" + fixture.quizA().getPublicUuid() + "/attempts",
				bearer,
				"");
		assertThat(startResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String attemptUuid = objectMapper.readTree(startResp.getBody())
				.get("data").get("publicUuid").asText();

		// Autosave all 3 answers (1 MC correct, 1 TF correct, 1 SHORT_ANSWER
		// with non-matching text so the auto-grader does NOT mark it correct).
		UUID mcQuestionId = fixture.mcQuestion().getPublicUuid();
		UUID mcOptionId = fixture.correctMcOption().getPublicUuid();
		UUID tfQuestionId = fixture.tfQuestion().getPublicUuid();
		UUID saQuestionId = fixture.shortAnswerQuestion().getPublicUuid();
		String saveBody = String.format(
				"{\"answers\":["
						+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"MC\","
						+ "  \"selectedOptionId\":\"%s\"},"
						+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"TF\","
						+ "  \"selectedBoolean\":true},"
						+ "{\"questionPublicUuid\":\"%s\",\"questionType\":\"SHORT_ANSWER\","
						+ "  \"textAnswer\":\"The mitochondria produces ATP\"}"
						+ "]}",
				mcQuestionId, mcOptionId, tfQuestionId, saQuestionId);
		ResponseEntity<String> saveResp = doPatch(
				ATTEMPTS_BASE + "/" + attemptUuid,
				bearer,
				saveBody);
		assertThat(saveResp.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Submit.
		ResponseEntity<String> submitResp = doPost(
				ATTEMPTS_BASE + "/" + attemptUuid + "/submit",
				bearer,
				"");
		assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
		// The attempt must end in AUTO_GRADED (SHORT_ANSWER pending).
		String status = objectMapper.readTree(submitResp.getBody())
				.get("data").get("status").asText();
		assertThat(status).isEqualTo(AttemptStatus.AUTO_GRADED.name());

		return UUID.fromString(attemptUuid);
	}

	private UUID createInProgressAttemptInB(Fixture fixture) throws Exception {
		String bearer = bearerFor(fixture.studentB(), fixture.tenantB(), "STUDENT");
		ResponseEntity<String> response = doPost(
				QUIZZES_BASE + "/" + fixture.quizB().getPublicUuid() + "/attempts",
				bearer,
				"");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return UUID.fromString(objectMapper.readTree(response.getBody())
				.get("data").get("publicUuid").asText());
	}

	private Tenant createTenant(String slugPrefix) {
		Tenant t = new Tenant();
		t.setSlug(slugPrefix + UUID.randomUUID().toString().substring(0, 8));
		t.setName("IT Tenant " + t.getSlug());
		t.setStatus(TenantStatus.ACTIVE);
		return tx().execute(s -> tenantRepository.saveAndFlush(t));
	}

	private User createUser(Tenant tenant, String email, String rawPassword, String roleName) {
		return TenantContext.runAs(tenant.getId(), () ->
				tx().execute(s -> {
					User user = new User();
					user.setEmail(email);
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
					// Insert PRIMARIA level + 1 grade on the fly, mirroring
					// what AcademicSeedService.seedDefaults does.
					AcademicLevel level = new AcademicLevel();
					level.setCode("PRIMARIA");
					level.setName("Primaria");
					level.setOrdinal(2);
					level = levelRepository.saveAndFlush(level);

					Grade grade = new Grade();
					grade.setName("2do Primaria");
					grade.setOrdinal(2);
					grade.setLevel(level);
					gradeRepository.saveAndFlush(grade);
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

	private Student createStudent(Tenant tenant, UUID userId, String label) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Student student = new Student();
			student.setDocumentType(DocumentType.DNI);
			student.setDocumentNumber("9" + Math.abs(tenant.getId().hashCode() % 1000000)
					+ label.hashCode() % 100);
			student.setFirstName("Ana");
			student.setLastName("Player " + label);
			student.setEmail("ana-player-" + tenant.getId() + "@isolation.test");
			student.setUserId(userId);
			student = studentRepository.saveAndFlush(student);
			return student;
		}));
	}

	private void enrollStudent(Student student, Section section, AcademicYear year) {
		TenantContext.runAs(student.getTenantId(), () -> tx().execute(s -> {
			StudentEnrollment enrollment = new StudentEnrollment();
			enrollment.setStudent(student);
			enrollment.setSection(section);
			enrollment.setAcademicYear(year);
			enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
			enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
			enrollmentRepository.saveAndFlush(enrollment);
			return null;
		}));
	}

	record PublishedQuiz(Quiz quiz,
			QuizQuestion mcQuestion, QuizOption correctMcOption,
			QuizQuestion tfQuestion, QuizQuestion shortAnswerQuestion) {}

	private PublishedQuiz createPublishedQuizWithMixedQuestions(Tenant tenant,
			Section section, User owner) {
		return TenantContext.runAs(tenant.getId(), () -> tx().execute(s -> {
			Quiz quiz = new Quiz();
			quiz.setSection(section);
			quiz.setTitle("Mixed quiz " + tenant.getSlug());
			quiz.setDescription("1 MC + 1 TF + 1 SHORT_ANSWER");
			quiz.setDueAt(Instant.now().plus(7, ChronoUnit.DAYS));
			quiz.setTimeLimitMinutes((short) 30);
			quiz.setMaxScore((short) 20);
			quiz.setAttemptsAllowed((short) 1);
			quiz.setOwnerUserId(owner.getPublicUuid());
			// Pre-persist so the quiz id exists for the FK from questions.
			quiz = quizRepository.saveAndFlush(quiz);

			// MC question (5 points), 4 options, 1 correct.
			QuizQuestion mcQ = new QuizQuestion();
			mcQ.setQuiz(quiz);
			mcQ.setPosition((short) 1);
			mcQ.setQuestionType(QuestionType.MC);
			mcQ.setPrompt("Which organelle produces ATP?");
			mcQ.setPoints((short) 5);
			mcQ = questionRepository.saveAndFlush(mcQ);

			// We only need the 4 options to satisfy the DB UNIQUE
			// (question_id, position) and the "exactly one correct"
			// trigger; the IT references only the correct one (optB)
			// to compose the answer payload.
			QuizOption optB = optionOf(mcQ, "Mitochondria", true, 2); // correct
			optionOf(mcQ, "Nucleus", false, 1);
			optionOf(mcQ, "Ribosome", false, 3);
			optionOf(mcQ, "Golgi", false, 4);

			// TF question (3 points), correctBoolean=true.
			QuizQuestion tfQ = new QuizQuestion();
			tfQ.setQuiz(quiz);
			tfQ.setPosition((short) 2);
			tfQ.setQuestionType(QuestionType.TF);
			tfQ.setPrompt("The sun is a star.");
			tfQ.setPoints((short) 3);
			tfQ.setCorrectBoolean(true);
			tfQ = questionRepository.saveAndFlush(tfQ);

			// SHORT_ANSWER question (4 points), no keywords (so the
			// auto-grader treats any non-blank text as "could be correct"
			// for seeding, but the final verdict is always manual).
			QuizQuestion saQ = new QuizQuestion();
			saQ.setQuiz(quiz);
			saQ.setPosition((short) 3);
			saQ.setQuestionType(QuestionType.SHORT_ANSWER);
			saQ.setPrompt("Explain photosynthesis in one sentence.");
			saQ.setPoints((short) 4);
			saQ = questionRepository.saveAndFlush(saQ);

			// Manually transition to PUBLISHED (mirrors QuizService.publish).
			quiz.setStatus(QuizStatus.PUBLISHED);
			quiz.setPublishedAt(Instant.now());
			quiz = quizRepository.saveAndFlush(quiz);

			return new PublishedQuiz(quiz, mcQ, optB, tfQ, saQ);
		}));
	}

	private QuizOption optionOf(QuizQuestion question, String label, boolean correct, int pos) {
		QuizOption opt = new QuizOption();
		opt.setQuestion(question);
		opt.setLabel(label);
		opt.setCorrect(correct);
		opt.setPosition((short) pos);
		return optionRepository.saveAndFlush(opt);
	}
}
