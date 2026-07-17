package com.edushift.modules.quizzes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.quizzes.dto.AnswerInput;
import com.edushift.modules.quizzes.dto.AttemptResponse;
import com.edushift.modules.quizzes.dto.GradingQueueItem;
import com.edushift.modules.quizzes.dto.ManualGradeAnswerRequest;
import com.edushift.modules.quizzes.dto.ManualGradeAttemptRequest;
import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.exception.AnswerNotFoundException;
import com.edushift.modules.quizzes.exception.AttemptNotFoundException;
import com.edushift.modules.quizzes.exception.AttemptNotInProgressException;
import com.edushift.modules.quizzes.exception.AttemptNotSubmittedException;
import com.edushift.modules.quizzes.exception.AttemptsExhaustedException;
import com.edushift.modules.quizzes.exception.QuizNotFoundException;
import com.edushift.modules.quizzes.exception.QuizNotPublishedException;
import com.edushift.modules.quizzes.exception.StudentNotEnrolledException;
import com.edushift.modules.quizzes.mapper.QuizAttemptMapper;
import com.edushift.modules.quizzes.repository.QuizAnswerRepository;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.impl.QuizAttemptServiceImpl;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.security.CurrentUserProvider;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link QuizAttemptServiceImpl}
 * (Sprint 7b / BE-7b.2).
 *
 * <p>Focuses on the business invariants specific to the attempt
 * + grading workflow:
 * <ul>
 *   <li>Start: PUBLISHED check, enrollment check, attempts-allowed
 *       counter.</li>
 *   <li>Submit: status guard, auto-grade round, score
 *       aggregation, AUTO_GRADED vs GRADED transition.</li>
 *   <li>Grade: SUBMITTED / AUTO_GRADED guard, feedback +
 *       gradedBy persistence, score re-count.</li>
 *   <li>Override: per-answer range check, re-count, anti-tamper
 *       (answer must belong to the attempt's quiz).</li>
 *   <li>Anti-enumeration: cross-tenant 404, caller≠student
 *       404 on taker endpoints.</li>
 * </ul>
 */
class QuizAttemptServiceValidationTest {

	private QuizRepository quizRepository;
	private QuizAttemptRepository attemptRepository;
	private QuizAnswerRepository answerRepository;
	private QuizQuestionRepository questionRepository;
	private QuizOptionRepository optionRepository;
	private SectionRepository sectionRepository;
	private StudentRepository studentRepository;
	private StudentEnrollmentRepository enrollmentRepository;
	private UserRepository userRepository;
	private QuizAttemptMapper attemptMapper;
	private CurrentUserProvider currentUserProvider;
	private ApplicationEventPublisher eventPublisher;
	private QuizAttemptServiceImpl service;

	@BeforeEach
	void setUp() {
		quizRepository = mock(QuizRepository.class);
		attemptRepository = mock(QuizAttemptRepository.class);
		answerRepository = mock(QuizAnswerRepository.class);
		questionRepository = mock(QuizQuestionRepository.class);
		optionRepository = mock(QuizOptionRepository.class);
		sectionRepository = mock(SectionRepository.class);
		studentRepository = mock(StudentRepository.class);
		enrollmentRepository = mock(StudentEnrollmentRepository.class);
		userRepository = mock(UserRepository.class);
		attemptMapper = new QuizAttemptMapper(answerRepository);
		currentUserProvider = mock(CurrentUserProvider.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		service = new QuizAttemptServiceImpl(
				quizRepository, attemptRepository, answerRepository,
				questionRepository, optionRepository, sectionRepository,
				studentRepository, enrollmentRepository, userRepository,
				attemptMapper, currentUserProvider, eventPublisher);
	}

	// ------------------------------------------------------------------
	// Start attempt
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Start attempt")
	class Start {

		@Test
		@DisplayName("missing quiz → 404 (anti-enumeration)")
		void missingQuizIs404() {
			UUID id = UUID.randomUUID();
			when(quizRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.startAttempt(id, UUID.randomUUID(), null))
					.isInstanceOf(QuizNotFoundException.class);
		}

		@Test
		@DisplayName("DRAFT quiz → QUIZ_NOT_PUBLISHED (409)")
		void draftQuizRejected() {
			Quiz quiz = publishedQuiz();
			setField(quiz, "status", QuizStatus.DRAFT);
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			assertThatThrownBy(() -> service.startAttempt(
					quiz.getPublicUuid(), UUID.randomUUID(), null))
					.isInstanceOf(QuizNotPublishedException.class);
		}

		@Test
		@DisplayName("student not enrolled in section → 403 STUDENT_NOT_ENROLLED")
		void unenrolledStudentRejected() {
			Quiz quiz = publishedQuiz();
			Section section = new Section();
			setField(section, "publicUuid", UUID.randomUUID());
			setField(quiz, "section", section);

			UUID studentUserId = UUID.randomUUID();
			UUID userInternalId = UUID.randomUUID();
			User user = new User();
			setField(user, "id", userInternalId);
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));
			when(userRepository.findByPublicUuid(studentUserId))
					.thenReturn(Optional.of(user));
			when(studentRepository.findByUserId(userInternalId))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.startAttempt(
					quiz.getPublicUuid(), studentUserId, null))
					.isInstanceOf(StudentNotEnrolledException.class);
			verify(attemptRepository, never()).save(any());
		}

		@Test
		@DisplayName("enrolled but inactive on today → 403 STUDENT_NOT_ENROLLED")
		void enrolledButInactiveRejected() {
			Quiz quiz = publishedQuiz();
			Section section = new Section();
			setField(section, "publicUuid", UUID.randomUUID());
			setField(quiz, "section", section);
			UUID studentUserId = UUID.randomUUID();
			UUID userInternalId = UUID.randomUUID();
			User user = new User();
			setField(user, "id", userInternalId);

			Student student = new Student();
			setField(student, "publicUuid", UUID.randomUUID());

			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));
			when(userRepository.findByPublicUuid(studentUserId))
					.thenReturn(Optional.of(user));
			when(studentRepository.findByUserId(userInternalId))
					.thenReturn(Optional.of(student));
			when(enrollmentRepository.existsActiveAt(
					eq(student), eq(section), any()))
					.thenReturn(false);

			assertThatThrownBy(() -> service.startAttempt(
					quiz.getPublicUuid(), studentUserId, null))
					.isInstanceOf(StudentNotEnrolledException.class);
		}

		@Test
		@DisplayName("attempts_allowed=1 and 1 attempt already exists → ATTEMPTS_EXHAUSTED")
		void attemptsExhaustedRejected() {
			Quiz quiz = publishedQuiz();
			setField(quiz, "attemptsAllowed", (short) 1);
			Section section = new Section();
			setField(section, "publicUuid", UUID.randomUUID());
			setField(quiz, "section", section);
			UUID studentUserId = UUID.randomUUID();
			UUID userInternalId = UUID.randomUUID();
			User user = new User();
			setField(user, "id", userInternalId);

			Student student = new Student();
			setField(student, "publicUuid", UUID.randomUUID());

			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));
			when(userRepository.findByPublicUuid(studentUserId))
					.thenReturn(Optional.of(user));
			when(studentRepository.findByUserId(userInternalId))
					.thenReturn(Optional.of(student));
			when(enrollmentRepository.existsActiveAt(
					eq(student), eq(section), any()))
					.thenReturn(true);
			when(attemptRepository.countByQuizAndStudentUserId(quiz, studentUserId))
					.thenReturn(1L);

			assertThatThrownBy(() -> service.startAttempt(
					quiz.getPublicUuid(), studentUserId, null))
					.isInstanceOf(AttemptsExhaustedException.class);
		}

		@Test
		@DisplayName("happy path → IN_PROGRESS row, attemptNumber=consumed+1, expiresAt set when timeLimitMinutes present")
		void happyPath() {
			Quiz quiz = publishedQuiz();
			setField(quiz, "attemptsAllowed", (short) 3);
			setField(quiz, "timeLimitMinutes", (short) 30);
			Section section = new Section();
			setField(section, "publicUuid", UUID.randomUUID());
			setField(quiz, "section", section);
			UUID studentUserId = UUID.randomUUID();
			UUID userInternalId = UUID.randomUUID();
			User user = new User();
			setField(user, "id", userInternalId);
			Student student = new Student();
			setField(student, "publicUuid", UUID.randomUUID());

			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));
			when(userRepository.findByPublicUuid(studentUserId))
					.thenReturn(Optional.of(user));
			when(studentRepository.findByUserId(userInternalId))
					.thenReturn(Optional.of(student));
			when(enrollmentRepository.existsActiveAt(
					eq(student), eq(section), any()))
					.thenReturn(true);
			when(attemptRepository.countByQuizAndStudentUserId(quiz, studentUserId))
					.thenReturn(1L);
			when(attemptRepository.save(any(QuizAttempt.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			AttemptResponse response = service.startAttempt(
					quiz.getPublicUuid(), studentUserId, studentUserId);

			assertThat(response.status()).isEqualTo(AttemptStatus.IN_PROGRESS);
			assertThat(response.attemptNumber()).isEqualTo((short) 2);
			assertThat(response.expiresAt()).isNotNull();
			assertThat(response.expiresAt()).isAfter(Instant.now());
			verify(attemptRepository, times(1)).save(any(QuizAttempt.class));
		}
	}

	// ------------------------------------------------------------------
	// Submit
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Submit")
	class Submit {

		@Test
		@DisplayName("submit on SUBMITTED attempt → ATTEMPT_NOT_IN_PROGRESS (409)")
		void submitTwiceRejected() {
			QuizAttempt attempt = inProgressAttempt();
			setField(attempt, "status", AttemptStatus.SUBMITTED);
			UUID attemptId = attempt.getPublicUuid();
			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));

			assertThatThrownBy(() -> service.submitAttempt(
					attemptId, attempt.getStudentUserId()))
					.isInstanceOf(AttemptNotInProgressException.class);
		}

		@Test
		@DisplayName("caller != student → 404 anti-enumeration (not 403)")
		void callerNotStudentIs404() {
			QuizAttempt attempt = inProgressAttempt();
			UUID attemptId = attempt.getPublicUuid();
			UUID otherUser = UUID.randomUUID();
			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));

			assertThatThrownBy(() -> service.submitAttempt(attemptId, otherUser))
					.isInstanceOf(AttemptNotFoundException.class);
		}

		@Test
		@DisplayName("happy path: 2 MC + 1 TF, all correct → GRADED, score=sum(points)")
		void autoGradeClosesTheAttempt() {
			QuizAttempt attempt = inProgressAttempt();
			Quiz quiz = attempt.getQuiz();
			setField(quiz, "maxScore", (short) 100);
			UUID attemptId = attempt.getPublicUuid();
			UUID caller = attempt.getStudentUserId();

			QuizQuestion q1 = mcQuestion((short) 5);
			QuizQuestion q2 = mcQuestion((short) 3);
			QuizQuestion q3 = tfQuestion(true, (short) 2);

			List<QuizQuestion> questions = List.of(q1, q2, q3);
			List<QuizOption> options = new ArrayList<>();
			for (QuizQuestion q : questions) {
				if (q.getQuestionType() == QuestionType.MC) {
					List<QuizOption> o = new ArrayList<>();
					o.add(optionFor(q, "A", false, 1));
					o.add(optionFor(q, "B", true, 2));
					o.add(optionFor(q, "C", false, 3));
					o.add(optionFor(q, "D", false, 4));
					options.addAll(o);
				}
			}
			QuizOption mc1Correct = options.get(1);
			QuizOption mc2Correct = options.get(5);

			QuizAnswer a1 = newAnswerWithSelectedOption(q1, mc1Correct.getPublicUuid());
			QuizAnswer a2 = newAnswerWithSelectedOption(q2, mc2Correct.getPublicUuid());
			QuizAnswer a3 = newAnswerWithSelectedBoolean(q3, true);

			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));
			when(answerRepository.findAllByAttemptOrderByQuestionPositionAsc(attempt))
					.thenReturn(List.of(a1, a2, a3));
			when(questionRepository.findAllByQuizOrderByPositionAsc(quiz))
					.thenReturn(questions);
			when(optionRepository.findAllByQuestionOrderByPositionAsc(any()))
					.thenAnswer(inv -> {
						QuizQuestion q = inv.getArgument(0);
						return options.stream()
								.filter(o -> o.getQuestion() != null
										&& o.getQuestion().getId().equals(q.getId()))
								.toList();
					});
			when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(attemptRepository.save(any(QuizAttempt.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			AttemptResponse response = service.submitAttempt(attemptId, caller);

			assertThat(response.status()).isEqualTo(AttemptStatus.GRADED);
			assertThat(response.autoScore()).isEqualTo(10); // 5 + 3 + 2
			assertThat(response.score()).isEqualTo(10);
			assertThat(attempt.getSubmittedAt()).isNotNull();
		}

		@Test
		@DisplayName("SHORT_ANSWER pending → AUTO_GRADED (not GRADED), manualScore still null")
		void shortAnswerKeepsItOpen() {
			QuizAttempt attempt = inProgressAttempt();
			Quiz quiz = attempt.getQuiz();
			setField(quiz, "maxScore", (short) 100);
			UUID attemptId = attempt.getPublicUuid();
			UUID caller = attempt.getStudentUserId();

			QuizQuestion qMc = mcQuestion((short) 5);
			QuizQuestion qSa = shortAnswerQuestion((short) 5,
					new String[]{"mitochondria"});
			List<QuizOption> opts = List.of(
					optionFor(qMc, "A", true, 1),
					optionFor(qMc, "B", false, 2));
			QuizAnswer aMc = newAnswerWithSelectedOption(qMc, opts.get(0).getPublicUuid());
			QuizAnswer aSa = newAnswerWithText(qSa, "nucleus stores DNA");

			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));
			when(answerRepository.findAllByAttemptOrderByQuestionPositionAsc(attempt))
					.thenReturn(List.of(aMc, aSa));
			when(questionRepository.findAllByQuizOrderByPositionAsc(quiz))
					.thenReturn(List.of(qMc, qSa));
			when(optionRepository.findAllByQuestionOrderByPositionAsc(any()))
					.thenAnswer(inv -> opts);
			when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(attemptRepository.save(any(QuizAttempt.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			AttemptResponse response = service.submitAttempt(attemptId, caller);

			assertThat(response.status()).isEqualTo(AttemptStatus.AUTO_GRADED);
			assertThat(response.autoScore()).isEqualTo(5);
			assertThat(response.manualScore()).isNull();
			assertThat(aMc.getCorrect()).isTrue();
			assertThat(aSa.getCorrect()).isFalse();
			assertThat(aSa.getPointsAwarded()).isEqualTo((short) 0);
		}
	}

	// ------------------------------------------------------------------
	// Manual grade + override
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Manual grading")
	class ManualGrade {

		@Test
		@DisplayName("gradeAttempt on IN_PROGRESS attempt → ATTEMPT_NOT_SUBMITTED (409)")
		void gradeInProgressRejected() {
			QuizAttempt attempt = inProgressAttempt();
			UUID attemptId = attempt.getPublicUuid();
			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));

			assertThatThrownBy(() -> service.gradeAttempt(
					attemptId,
					new ManualGradeAttemptRequest(List.of(), null),
					UUID.randomUUID()))
					.isInstanceOf(AttemptNotSubmittedException.class);
		}

		@Test
		@DisplayName("overrideAnswerGrade with points > question.points → GRADE_OUT_OF_RANGE (400)")
		void overrideOutOfRangeRejected() {
			QuizAttempt attempt = inProgressAttempt();
			setField(attempt, "status", AttemptStatus.SUBMITTED);
			UUID attemptId = attempt.getPublicUuid();
			Quiz quiz = attempt.getQuiz();
			UUID quizId = quiz.getPublicUuid();

			QuizQuestion q = mcQuestion((short) 5);
			setField(q, "quiz", quiz);  // tie the question to the attempt's quiz
			QuizAnswer a = newAnswerWithSelectedOption(q, UUID.randomUUID());
			setField(a, "attempt", attempt);  // tie the answer to the attempt
			UUID answerId = a.getPublicUuid();
			UUID grader = UUID.randomUUID();

			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));
			when(answerRepository.findByPublicUuid(answerId))
					.thenReturn(Optional.of(a));
			when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(attemptRepository.save(any(QuizAttempt.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			ManualGradeAnswerRequest g = new ManualGradeAnswerRequest(
					answerId, /* pointsAwarded */ 99);

			assertThatThrownBy(() -> service.overrideAnswerGrade(
					quizId, attemptId, answerId, g, grader))
					.isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
					.hasMessageContaining("[0, 5]")
					.hasFieldOrPropertyWithValue("code", "GRADE_OUT_OF_RANGE");
		}

		@Test
		@DisplayName("overrideAnswerGrade with answer from a different attempt → AnswerNotFoundException (anti-tamper)")
		void overrideFromDifferentAttemptIs404() {
			QuizAttempt attempt = inProgressAttempt();
			setField(attempt, "status", AttemptStatus.SUBMITTED);
			UUID attemptId = attempt.getPublicUuid();
			Quiz quiz = attempt.getQuiz();

			QuizQuestion q = mcQuestion((short) 5);
			// The answer belongs to a *different* attempt
			// (the entity's attempt field is set to a different
			// QuizAttempt instance).
			QuizAttempt foreign = new QuizAttempt();
			setField(foreign, "id", UUID.randomUUID());
			setField(foreign, "publicUuid", UUID.randomUUID());
			setField(foreign, "quiz", quiz);
			QuizAnswer a = new QuizAnswer();
			setField(a, "id", UUID.randomUUID());
			setField(a, "publicUuid", UUID.randomUUID());
			setField(a, "attempt", foreign);
			setField(a, "question", q);
			UUID answerId = a.getPublicUuid();
			UUID grader = UUID.randomUUID();

			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));
			when(answerRepository.findByPublicUuid(answerId))
					.thenReturn(Optional.of(a));

			ManualGradeAnswerRequest g = new ManualGradeAnswerRequest(
					answerId, 3);

			assertThatThrownBy(() -> service.overrideAnswerGrade(
					quiz.getPublicUuid(), attemptId, answerId, g, grader))
					.isInstanceOf(AnswerNotFoundException.class);
		}

		@Test
		@DisplayName("gradeAttempt happy path: SUBMITTED → GRADED, feedback persisted, score recomputed")
		void gradeAttemptClosesIt() {
			QuizAttempt attempt = inProgressAttempt();
			setField(attempt, "status", AttemptStatus.AUTO_GRADED);
			UUID attemptId = attempt.getPublicUuid();
			UUID grader = UUID.randomUUID();

			QuizQuestion qSa = shortAnswerQuestion((short) 5,
					new String[]{"mitochondria"});
			QuizAnswer aSa = newAnswerWithText(qSa, "anything");
			setField(aSa, "attempt", attempt);  // tie the answer to the attempt
			UUID answerId = aSa.getPublicUuid();
			setField(aSa, "pointsAwarded", null);

			when(attemptRepository.findByPublicUuid(attemptId))
					.thenReturn(Optional.of(attempt));
			when(answerRepository.findAllByAttemptOrderByQuestionPositionAsc(attempt))
					.thenReturn(List.of(aSa));
			when(answerRepository.findByPublicUuid(answerId))
					.thenReturn(Optional.of(aSa));
			when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(attemptRepository.save(any(QuizAttempt.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			ManualGradeAttemptRequest req = new ManualGradeAttemptRequest(
					List.of(new ManualGradeAnswerRequest(answerId, 4)),
					"Bien, pero falta precisión.");

			AttemptResponse response = service.gradeAttempt(attemptId, req, grader);

			assertThat(response.status()).isEqualTo(AttemptStatus.GRADED);
			assertThat(attempt.getGradedByUserId()).isEqualTo(grader);
			assertThat(attempt.getGradedAt()).isNotNull();
			assertThat(attempt.getFeedback()).isEqualTo("Bien, pero falta precisión.");
			assertThat(attempt.getManualScore()).isEqualTo((short) 4);
			assertThat(attempt.getScore()).isEqualTo((short) 4);
			assertThat(aSa.getPointsAwarded()).isEqualTo((short) 4);
			assertThat(aSa.getGradedAt()).isNotNull();
		}
	}

	// ------------------------------------------------------------------
	// Grading queue
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Grading queue")
	class Queue {

		@Test
		@DisplayName("missing quiz → 404")
		void missingQuizIs404() {
			UUID id = UUID.randomUUID();
			when(quizRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getGradingQueue(id))
					.isInstanceOf(QuizNotFoundException.class);
		}

		@Test
		@DisplayName("flattens pending SHORT_ANSWER answers across all attempts of a quiz")
		void flatten() {
			Quiz quiz = publishedQuiz();
			UUID quizId = quiz.getPublicUuid();
			QuizQuestion q = shortAnswerQuestion((short) 5, null);
			QuizAttempt a1 = inProgressAttempt();
			QuizAttempt a2 = inProgressAttempt();
			QuizAnswer ans1 = newAnswerWithText(q, "answer 1");
			QuizAnswer ans2 = newAnswerWithText(q, "answer 2");

			when(quizRepository.findByPublicUuid(quizId))
					.thenReturn(Optional.of(quiz));
			Page<QuizAttempt> page = new PageImpl<>(List.of(a1, a2));
			when(attemptRepository.findAllByQuizOrderByAttemptNumberAsc(
					eq(quiz), any(Pageable.class))).thenReturn(page);
			when(answerRepository
					.findAllByAttemptAndTextAnswerIsNotNullAndGradedAtIsNull(a1))
					.thenReturn(List.of(ans1));
			when(answerRepository
					.findAllByAttemptAndTextAnswerIsNotNullAndGradedAtIsNull(a2))
					.thenReturn(List.of(ans2));

			List<GradingQueueItem> queue = service.getGradingQueue(quizId);

			assertThat(queue).hasSize(2);
			assertThat(queue).extracting(GradingQueueItem::textAnswer)
					.containsExactlyInAnyOrder("answer 1", "answer 2");
		}
	}

	// ------------------------------------------------------------------
	// Builders
	// ------------------------------------------------------------------

	private static Quiz publishedQuiz() {
		Quiz q = new Quiz();
		setField(q, "id", UUID.randomUUID());
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "status", QuizStatus.PUBLISHED);
		setField(q, "title", "test");
		setField(q, "maxScore", (short) 100);
		setField(q, "attemptsAllowed", (short) 1);
		setField(q, "publishedAt", Instant.now());
		return q;
	}

	private static QuizAttempt inProgressAttempt() {
		QuizAttempt a = new QuizAttempt();
		setField(a, "id", UUID.randomUUID());
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "status", AttemptStatus.IN_PROGRESS);
		setField(a, "studentUserId", UUID.randomUUID());
		setField(a, "submitterUserId", a.getStudentUserId());
		setField(a, "attemptNumber", (short) 1);
		setField(a, "startedAt", Instant.now());
		setField(a, "quiz", publishedQuiz());
		return a;
	}

	private static QuizQuestion mcQuestion(short points) {
		QuizQuestion q = new QuizQuestion();
		setField(q, "id", UUID.randomUUID());
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "questionType", QuestionType.MC);
		setField(q, "prompt", "mc?");
		setField(q, "points", points);
		setField(q, "position", (short) 1);
		return q;
	}

	private static QuizQuestion tfQuestion(boolean correctAnswer, short points) {
		QuizQuestion q = new QuizQuestion();
		setField(q, "id", UUID.randomUUID());
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "questionType", QuestionType.TF);
		setField(q, "prompt", "true?");
		setField(q, "points", points);
		setField(q, "position", (short) 1);
		setField(q, "correctBoolean", correctAnswer);
		return q;
	}

	private static QuizQuestion shortAnswerQuestion(short points,
			String[] keywords) {
		QuizQuestion q = new QuizQuestion();
		setField(q, "id", UUID.randomUUID());
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "questionType", QuestionType.SHORT_ANSWER);
		setField(q, "prompt", "explain");
		setField(q, "points", points);
		setField(q, "position", (short) 1);
		setField(q, "expectedKeywords", keywords);
		return q;
	}

	private static QuizOption optionFor(QuizQuestion q, String label,
			boolean correct, int pos) {
		QuizOption o = new QuizOption();
		setField(o, "id", UUID.randomUUID());
		setField(o, "publicUuid", UUID.randomUUID());
		setField(o, "question", q);
		setField(o, "label", label);
		setField(o, "correct", correct);
		setField(o, "position", (short) pos);
		return o;
	}

	private static QuizAnswer newAnswerWithSelectedOption(QuizQuestion q,
			UUID optionId) {
		QuizAnswer a = new QuizAnswer();
		setField(a, "id", UUID.randomUUID());
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "question", q);
		setField(a, "selectedOptionId", optionId);
		return a;
	}

	private static QuizAnswer newAnswerWithSelectedBoolean(QuizQuestion q,
			Boolean b) {
		QuizAnswer a = new QuizAnswer();
		setField(a, "id", UUID.randomUUID());
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "question", q);
		setField(a, "selectedBoolean", b);
		return a;
	}

	private static QuizAnswer newAnswerWithText(QuizQuestion q, String text) {
		QuizAnswer a = new QuizAnswer();
		setField(a, "id", UUID.randomUUID());
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "question", q);
		setField(a, "textAnswer", text);
		return a;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = findField(target.getClass(), name);
			f.setAccessible(true);
			f.set(target, value);
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to set " + name + " on "
					+ target.getClass().getSimpleName(), ex);
		}
	}

	private static Field findField(Class<?> c, String name)
			throws NoSuchFieldException {
		while (c != null) {
			try {
				return c.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignored) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

	// helpers used inside @Nested
	private static <T> T eq(T v) {
		return org.mockito.ArgumentMatchers.eq(v);
	}
}
