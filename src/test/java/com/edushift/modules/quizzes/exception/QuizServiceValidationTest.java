package com.edushift.modules.quizzes.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.quizzes.dto.AddOptionRequest;
import com.edushift.modules.quizzes.dto.CreateOptionRequest;
import com.edushift.modules.quizzes.dto.CreateQuestionRequest;
import com.edushift.modules.quizzes.dto.CreateQuizRequest;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.UpdateQuizRequest;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.mapper.QuizMapper;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import com.edushift.modules.quizzes.service.impl.QuizServiceImpl;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link QuizServiceImpl} (Sprint 7b / BE-7b.1).
 *
 * <p>Focuses on the business invariants that are not trivially
 * covered by the integration test layer:
 * <ul>
 *   <li>Lifecycle transitions (DRAFT → PUBLISHED → CLOSED).</li>
 *   <li>MC question invariants (2-6 options, exactly one
 *       correct).</li>
 *   <li>Empty-patch rejection.</li>
 *   <li>Cross-tenant lookup → 404 (anti-enumeration).</li>
 * </ul>
 *
 * <p>Uses Mockito for the repositories; mapper is real.
 */
class QuizServiceValidationTest {

	private QuizRepository quizRepository;
	private QuizQuestionRepository questionRepository;
	private QuizOptionRepository optionRepository;
	private SectionRepository sectionRepository;
	private QuizMapper quizMapper;
	private QuizAttemptService attemptService;
	private ApplicationEventPublisher eventPublisher;
	private StudentEnrollmentRepository enrollmentRepository;
	private QuizServiceImpl service;

	@BeforeEach
	void setUp() {
		quizRepository = mock(QuizRepository.class);
		questionRepository = mock(QuizQuestionRepository.class);
		optionRepository = mock(QuizOptionRepository.class);
		sectionRepository = mock(SectionRepository.class);
		quizMapper = new QuizMapper(questionRepository, optionRepository);
		attemptService = mock(QuizAttemptService.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		enrollmentRepository = mock(StudentEnrollmentRepository.class);
		service = new QuizServiceImpl(
				quizRepository, questionRepository, optionRepository,
				sectionRepository, quizMapper, attemptService,
				eventPublisher, enrollmentRepository);
	}

	// ------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Lifecycle")
	class Lifecycle {

		@Test
		@DisplayName("publish on a DRAFT quiz with 0 questions throws QUIZ_HAS_NO_QUESTIONS")
		void publishEmptyQuizRejected() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));
			when(questionRepository.countByQuiz(quiz)).thenReturn(0L);

			assertThatThrownBy(() -> service.publish(quiz.getPublicUuid()))
					.isInstanceOf(InvalidQuizStateException.class)
					.hasMessageContaining("Cannot publish a quiz with zero questions");
			verify(quizRepository, never()).save(any());
		}

		@Test
		@DisplayName("publish on a non-DRAFT quiz throws QUIZ_NOT_DRAFT")
		void publishNonDraftRejected() {
			Quiz quiz = draftQuiz();
			setField(quiz, "status", QuizStatus.PUBLISHED);
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			assertThatThrownBy(() -> service.publish(quiz.getPublicUuid()))
					.isInstanceOf(InvalidQuizStateException.class)
					.hasMessageContaining("DRAFT");
		}

		@Test
		@DisplayName("close on a DRAFT quiz throws QUIZ_NOT_PUBLISHED")
		void closeDraftRejected() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			assertThatThrownBy(() -> service.close(quiz.getPublicUuid()))
					.isInstanceOf(InvalidQuizStateException.class)
					.hasMessageContaining("PUBLISHED");
		}

		@Test
		@DisplayName("close on an already CLOSED quiz throws QUIZ_ALREADY_CLOSED")
		void closeAlreadyClosedRejected() {
			Quiz quiz = draftQuiz();
			setField(quiz, "status", QuizStatus.CLOSED);
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			assertThatThrownBy(() -> service.close(quiz.getPublicUuid()))
					.isInstanceOf(InvalidQuizStateException.class)
					.hasMessageContaining("CLOSED");
		}

		@Test
		@DisplayName("publish on a DRAFT quiz with ≥1 question succeeds and sets publishedAt")
		void publishHappyPath() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));
			when(questionRepository.countByQuiz(quiz)).thenReturn(3L);
			when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

			QuizResponse response = service.publish(quiz.getPublicUuid());

			assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
			assertThat(quiz.getPublishedAt()).isNotNull();
			assertThat(quiz.getPublishedAt()).isBeforeOrEqualTo(Instant.now());
			assertThat(response).isNotNull();
			verify(quizRepository, times(1)).save(any(Quiz.class));
		}
	}

	// ------------------------------------------------------------------
	// Patch
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Patch")
	class Patch {

		@Test
		@DisplayName("empty PATCH throws QUIZ_RECORD_EMPTY_PATCH")
		void emptyPatchRejected() {
			assertThatThrownBy(() -> service.patch(UUID.randomUUID(), emptyUpdate()))
					.isInstanceOf(RecordEmptyPatchException.class);
			verify(quizRepository, never()).findByPublicUuid(any());
		}

		@Test
		@DisplayName("non-DRAFT quiz rejects title edits with QUIZ_NOT_DRAFT")
		void publishedTitleEditRejected() {
			Quiz quiz = draftQuiz();
			setField(quiz, "status", QuizStatus.PUBLISHED);
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			UpdateQuizRequest r = new UpdateQuizRequest(
					"new title", null, null, null, null, null);
			assertThatThrownBy(() -> service.patch(quiz.getPublicUuid(), r))
					.isInstanceOf(InvalidQuizStateException.class)
					.hasMessageContaining("DRAFT");
		}
	}

	// ------------------------------------------------------------------
	// MC question invariants
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("MC question invariants")
	class McInvariants {

		@Test
		@DisplayName("addQuestion with 1 option throws MC_QUESTION_NEEDS_2_TO_6_OPTIONS")
		void tooFewOptions() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			CreateQuestionRequest r = new CreateQuestionRequest(
					QuestionType.MC, "Pick one", 1, 1, null, null, null,
					List.of(new CreateOptionRequest("only one", true, null)));

			assertThatThrownBy(() -> service.addQuestion(quiz.getPublicUuid(), r))
					.isInstanceOf(QuestionValidationException.class)
					.hasMessageContaining("2 to 6 options");
		}

		@Test
		@DisplayName("addQuestion with 0 correct options throws MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT")
		void noCorrectOption() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			CreateQuestionRequest r = new CreateQuestionRequest(
					QuestionType.MC, "Pick one", 1, 1, null, null, null,
					List.of(
							new CreateOptionRequest("A", false, null),
							new CreateOptionRequest("B", false, null)));

			assertThatThrownBy(() -> service.addQuestion(quiz.getPublicUuid(), r))
					.isInstanceOf(QuestionValidationException.class)
					.hasMessageContaining("exactly one option flagged is_correct=true");
		}

		@Test
		@DisplayName("TF with options throws TF_QUESTION_HAS_OPTIONS")
		void tfWithOptions() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			CreateQuestionRequest r = new CreateQuestionRequest(
					QuestionType.TF, "Is X true?", 1, 1, null, null, true,
					List.of(new CreateOptionRequest("yes", true, null)));

			assertThatThrownBy(() -> service.addQuestion(quiz.getPublicUuid(), r))
					.isInstanceOf(QuestionValidationException.class)
					.hasMessageContaining("TF questions cannot have options");
		}

		@Test
		@DisplayName("SHORT_ANSWER with options throws SHORT_ANSWER_HAS_OPTIONS")
		void shortAnswerWithOptions() {
			Quiz quiz = draftQuiz();
			when(quizRepository.findByPublicUuid(quiz.getPublicUuid()))
					.thenReturn(Optional.of(quiz));

			CreateQuestionRequest r = new CreateQuestionRequest(
					QuestionType.SHORT_ANSWER, "Explain…", 5, 1, "any", null, null,
					List.of(new CreateOptionRequest("keyword", true, null)));

			assertThatThrownBy(() -> service.addQuestion(quiz.getPublicUuid(), r))
					.isInstanceOf(QuestionValidationException.class)
					.hasMessageContaining("SHORT_ANSWER questions cannot have options");
		}
	}

	// ------------------------------------------------------------------
	// Cross-tenant 404
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Anti-enumeration")
	class AntiEnumeration {

		@Test
		@DisplayName("missing quiz → QUIZ_NOT_FOUND (404)")
		void missingQuizIs404() {
			UUID id = UUID.randomUUID();
			when(quizRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getByPublicUuid(id))
					.isInstanceOf(QuizNotFoundException.class);
		}

		@Test
		@DisplayName("missing section in create → SECTION_NOT_FOUND (404)")
		void missingSectionIs404() {
			UUID id = UUID.randomUUID();
			when(sectionRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			CreateQuizRequest r = new CreateQuizRequest(
					"new", null, null, 30, 1, 100, null);

			assertThatThrownBy(() -> service.create(id, r, UUID.randomUUID()))
					.isInstanceOf(SectionNotFoundException.class);
		}
	}

	// ------------------------------------------------------------------
	// Builders
	// ------------------------------------------------------------------

	private static Quiz draftQuiz() {
		Quiz q = new Quiz();
		setField(q, "publicUuid", UUID.randomUUID());
		setField(q, "status", QuizStatus.DRAFT);
		setField(q, "title", "test quiz");
		setField(q, "maxScore", (short) 100);
		setField(q, "attemptsAllowed", (short) 1);
		return q;
	}

	private static Section section() {
		Section s = new Section();
		setField(s, "publicUuid", UUID.randomUUID());
		return s;
	}

	private static UpdateQuizRequest emptyUpdate() {
		return new UpdateQuizRequest(null, null, null, null, null, null);
	}

	// Suppress unused warning on `section()` helper (kept for future
	// createSection tests).
	@SuppressWarnings("unused")
	private static Section _section() { return section(); }

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

	private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
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

	// Suppress unused `optionRepository` for tests that don't need it.
	@SuppressWarnings("unused")
	private void touchOptionRepo() {
		when(optionRepository.countByQuestionAndCorrectIsTrue(any())).thenReturn(0L);
	}
}
