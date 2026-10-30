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
import com.edushift.modules.quizzes.dto.AttemptSummary;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.repository.QuizAnswerRepository;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import com.edushift.modules.quizzes.service.QuizService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the quiz read paths stay a fixed number of queries
 * when the page grows (4 quizzes, 4 questions, 6 attempts).
 */
@DisplayName("Quiz query budget")
class QuizQueryBudgetIT extends IntegrationTest {

	private static final int MC_QUIZZES = 4;
	private static final int QUESTIONS_PER_QUIZ = 4;
	private static final int POINTS_PER_QUESTION = 2;
	private static final int ATTEMPTS = 6;
	private static final int PENDING_SHORT_ANSWERS = 2;

	@Autowired private QuizService quizService;
	@Autowired private QuizAttemptService attemptService;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private AcademicLevelRepository levelRepository;
	@Autowired private GradeRepository gradeRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private QuizRepository quizRepository;
	@Autowired private QuizQuestionRepository questionRepository;
	@Autowired private QuizOptionRepository optionRepository;
	@Autowired private QuizAttemptRepository attemptRepository;
	@Autowired private QuizAnswerRepository answerRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManager entityManager;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private PlatformTransactionManager txManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private TransactionTemplate tx;
	private UUID tenantId;
	private UUID ownerPublicUuid;
	private UUID studentPublicUuid;
	private UUID sectionPublicUuid;
	private UUID detailQuizPublicUuid;
	private UUID gradingQuizPublicUuid;

	@BeforeEach
	void seed() {
		tx = new TransactionTemplate(txManager);
		statistics().setStatisticsEnabled(true);

		Tenant tenant = newTestTenant();
		tenant.setSlug("qb" + UUID.randomUUID().toString().substring(0, 8));
		tenant.setName("Query budget " + tenant.getSlug());
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant.setPlan(TenantPlan.BASIC);
		tenant.setPlanId(jdbcTemplate.queryForObject(
				"select id from edushift.platform_plans where code = ?",
				UUID.class, "BASIC"));
		Tenant saved = tx.execute(s -> tenantRepository.saveAndFlush(tenant));
		tenantId = saved.getId();
		seedUsers();

		sectionPublicUuid = seedSection();
		detailQuizPublicUuid = seedMcQuizzes();
		gradingQuizPublicUuid = seedGradingQuiz();
	}

	@Test
	@DisplayName("list, detail, grading queue and attempt page stay within a fixed query budget")
	void queryCountsDoNotGrowWithPageSize() {
		long listQueries = measure(() -> {
			var page = quizService.listBySection(sectionPublicUuid, PageRequest.of(0, 20));
			assertThat(page.getContent()).hasSize(MC_QUIZZES + 1);
			assertThat(page.getContent())
					.filteredOn(summary -> summary.questionCount() == QUESTIONS_PER_QUIZ)
					.hasSize(MC_QUIZZES)
					.allSatisfy(summary -> assertThat(summary.totalPoints())
							.isEqualTo(QUESTIONS_PER_QUIZ * POINTS_PER_QUESTION));
			return page;
		});
		assertThat(listQueries).as("listBySection queries").isLessThanOrEqualTo(6);

		long detailQueries = measure(() -> {
			QuizResponse detail = quizService.getByPublicUuid(detailQuizPublicUuid);
			assertThat(detail.questionCount()).isEqualTo(QUESTIONS_PER_QUIZ);
			assertThat(detail.totalPoints()).isEqualTo(QUESTIONS_PER_QUIZ * POINTS_PER_QUESTION);
			assertThat(detail.questions()).hasSize(QUESTIONS_PER_QUIZ)
					.allSatisfy(question -> assertThat(question.options()).hasSize(2));
			return detail;
		});
		assertThat(detailQueries).as("getByPublicUuid queries").isLessThanOrEqualTo(6);

		long queueQueries = measure(() -> {
			var queue = attemptService.getGradingQueue(gradingQuizPublicUuid);
			assertThat(queue).hasSize(PENDING_SHORT_ANSWERS);
			assertThat(queue).allSatisfy(row -> assertThat(row.textAnswer()).isNotBlank());
			return queue;
		});
		assertThat(queueQueries).as("getGradingQueue queries").isLessThanOrEqualTo(4);

		long attemptQueries = measure(() -> {
			var page = attemptService.listAttempts(gradingQuizPublicUuid, PageRequest.of(0, 20));
			assertThat(page.getContent()).hasSize(ATTEMPTS);
			assertThat(page.getContent())
					.filteredOn(summary -> summary.pendingAnswerCount() == 1)
					.hasSize(PENDING_SHORT_ANSWERS);
			assertThat(page.getContent().stream().mapToInt(AttemptSummary::pendingAnswerCount).sum())
					.isEqualTo(PENDING_SHORT_ANSWERS);
			return page;
		});
		assertThat(attemptQueries).as("listAttempts queries").isLessThanOrEqualTo(5);
	}

	private <T> long measure(Supplier<T> call) {
		tx.executeWithoutResult(status -> entityManager.clear());
		Statistics stats = statistics();
		stats.clear();
		TenantContext.runAs(tenantId, call);
		return stats.getQueryExecutionCount();
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private void seedUsers() {
		TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			ownerPublicUuid = saveUser("owner", UserRole.TEACHER).getPublicUuid();
			studentPublicUuid = saveUser("student", UserRole.STUDENT).getPublicUuid();
			return null;
		}));
	}

	private User saveUser(String localPart, UserRole role) {
		User user = new User();
		user.setEmail(localPart + "-" + tenantId + "@query-budget.test");
		user.setPasswordHash("$2a$10$dummy.hash.for.it.only.satisfies.not.null");
		user.setFirstName(localPart);
		user.setLastName("Budget");
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(true);
		user.addRole(role);
		return userRepository.saveAndFlush(user);
	}

	private UUID seedSection() {
		return TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			AcademicLevel level = new AcademicLevel();
			level.setCode("PRIMARIA");
			level.setName("Primaria");
			level.setOrdinal(2);
			level = levelRepository.saveAndFlush(level);

			Grade grade = new Grade();
			grade.setName("2do Primaria");
			grade.setOrdinal(2);
			grade.setLevel(level);
			grade = gradeRepository.saveAndFlush(grade);

			AcademicYear year = new AcademicYear();
			year.setName("2026");
			year.setStatus(AcademicYearStatus.ACTIVE);
			year.setStartDate(LocalDate.of(2026, 3, 1));
			year.setEndDate(LocalDate.of(2026, 12, 15));
			year = yearRepository.saveAndFlush(year);

			Section section = new Section();
			section.setAcademicYear(year);
			section.setGrade(grade);
			section.setName("A");
			section.setDisplayOrder(1);
			return sectionRepository.saveAndFlush(section).getPublicUuid();
		}));
	}

	private UUID seedMcQuizzes() {
		return TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			Section section = sectionRepository.findByPublicUuid(sectionPublicUuid).orElseThrow();
			UUID firstQuiz = null;
			for (int i = 0; i < MC_QUIZZES; i++) {
				Quiz quiz = newQuiz(section, "MC " + i, QuizStatus.PUBLISHED, ownerPublicUuid);
				quiz = quizRepository.saveAndFlush(quiz);
				if (firstQuiz == null) {
					firstQuiz = quiz.getPublicUuid();
				}
				for (int q = 1; q <= QUESTIONS_PER_QUIZ; q++) {
					QuizQuestion question = new QuizQuestion();
					question.setQuiz(quiz);
					question.setPosition((short) q);
					question.setQuestionType(QuestionType.MC);
					question.setPrompt("Q" + q);
					question.setPoints((short) POINTS_PER_QUESTION);
					question = questionRepository.saveAndFlush(question);
					option(question, "A", true, 1);
					option(question, "B", false, 2);
				}
			}
			return firstQuiz;
		}));
	}

	private UUID seedGradingQuiz() {
		return TenantContext.runAs(tenantId, () -> tx.execute(status -> {
			Section section = sectionRepository.findByPublicUuid(sectionPublicUuid).orElseThrow();
			Quiz quiz = newQuiz(section, "Short answers", QuizStatus.PUBLISHED, ownerPublicUuid);
			quiz.setAttemptsAllowed((short) ATTEMPTS);
			quiz = quizRepository.saveAndFlush(quiz);

			QuizQuestion question = new QuizQuestion();
			question.setQuiz(quiz);
			question.setPosition((short) 1);
			question.setQuestionType(QuestionType.SHORT_ANSWER);
			question.setPrompt("Explain.");
			question.setPoints((short) 4);
			question = questionRepository.saveAndFlush(question);

			for (int i = 0; i < ATTEMPTS; i++) {
				QuizAttempt attempt = new QuizAttempt();
				attempt.setQuiz(quiz);
				attempt.setStudentUserId(studentPublicUuid);
				attempt.setSubmitterUserId(studentPublicUuid);
				attempt.setAttemptNumber((short) (i + 1));
				attempt.setStatus(AttemptStatus.SUBMITTED);
				attempt.setStartedAt(Instant.now());
				attempt.setSubmittedAt(Instant.now());
				attempt = attemptRepository.saveAndFlush(attempt);
				if (i < PENDING_SHORT_ANSWERS) {
					QuizAnswer answer = new QuizAnswer();
					answer.setAttempt(attempt);
					answer.setQuestion(question);
					answer.setTextAnswer("pending-" + i);
					answerRepository.saveAndFlush(answer);
				}
			}
			return quiz.getPublicUuid();
		}));
	}

	private static Quiz newQuiz(Section section, String title, QuizStatus status, UUID ownerPublicUuid) {
		Quiz quiz = new Quiz();
		quiz.setSection(section);
		quiz.setTitle(title);
		quiz.setStatus(status);
		quiz.setPublishedAt(Instant.now());
		quiz.setMaxScore((short) 100);
		quiz.setAttemptsAllowed((short) 1);
		quiz.setOwnerUserId(ownerPublicUuid);
		quiz.setDueAt(Instant.parse("2026-12-01T00:00:00Z"));
		return quiz;
	}

	private void option(QuizQuestion question, String label, boolean correct, int position) {
		QuizOption option = new QuizOption();
		option.setQuestion(question);
		option.setLabel(label);
		option.setCorrect(correct);
		option.setPosition((short) position);
		optionRepository.saveAndFlush(option);
	}
}
