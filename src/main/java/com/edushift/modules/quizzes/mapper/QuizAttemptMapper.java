package com.edushift.modules.quizzes.mapper;

import com.edushift.modules.quizzes.dto.AnswerResponse;
import com.edushift.modules.quizzes.dto.AttemptResponse;
import com.edushift.modules.quizzes.dto.AttemptSummary;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.repository.QuizAnswerRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mapper for the {@code lms_quiz_attempts} aggregate
 * (Sprint 7b / BE-7b.2). Mirrors the design of
 * {@link QuizMapper}: the {@link QuizAttempt} entity has no JPA
 * child collections, so the mapper pulls answers from
 * {@link QuizAnswerRepository} on demand.
 *
 * <h3>revealCorrectness pass-through</h3>
 * The service decides who sees the grading outcome: a STUDENT
 * only sees {@code correct} / {@code pointsAwarded} on answers
 * once their own attempt is in {@code GRADED} state; a TEACHER
 * with grading authority always sees it. The mapper simply
 * echoes the flag through and the {@link #toAnswerResponse}
 * builder drops the grade fields when {@code revealCorrectness}
 * is false.
 */
@Component
public class QuizAttemptMapper {

	private final QuizAnswerRepository answerRepository;

	public QuizAttemptMapper(QuizAnswerRepository answerRepository) {
		this.answerRepository = answerRepository;
	}

	// ------------------------------------------------------------------
	// Attempt → AttemptResponse
	// ------------------------------------------------------------------

	/**
	 * Build an {@link AttemptResponse} from the entity, with
	 * answers resolved and (optionally) grading state revealed.
	 *
	 * @param attempt            the attempt entity.
	 * @param quiz               the parent quiz (needed to expose
	 *                           {@code maxScore} without a second
	 *                           DB roundtrip).
	 * @param revealCorrectness  when true, the
	 *                           {@code correct} / {@code pointsAwarded}
	 *                           fields are populated on the answer
	 *                           rows.
	 * @param pendingAnswerCount number of SHORT_ANSWER answers in
	 *                           this attempt still awaiting manual
	 *                           grading (used by FE-7b.3 to render
	 *                           "3 of 5 graded" in the results
	 *                           screen). When negative the field is
	 *                           omitted.
	 */
	public AttemptResponse toResponse(QuizAttempt attempt, Quiz quiz,
			boolean revealCorrectness, int pendingAnswerCount) {
		List<QuizAnswer> answers = answerRepository
				.findAllByAttemptOrderByQuestionPositionAsc(attempt);
		List<AnswerResponse> answerDtos = answers.stream()
				.filter(a -> !a.isDeleted())
				.map(a -> toAnswerResponse(a, revealCorrectness))
				.toList();

		Integer timeRemaining = computeTimeRemaining(attempt);

		return new AttemptResponse(
				attempt.getPublicUuid(),
				quiz != null ? quiz.getPublicUuid() : null,
				attempt.getStudentUserId(),
				attempt.getSubmitterUserId(),
				attempt.getAttemptNumber(),
				attempt.getStatus(),
				attempt.getStartedAt(),
				attempt.getSubmittedAt(),
				attempt.getExpiresAt(),
				timeRemaining,
				toInt(attempt.getAutoScore()),
				toInt(attempt.getManualScore()),
				toInt(attempt.getScore()),
				quiz != null ? toInt(quiz.getMaxScore()) : null,
				attempt.getGradedByUserId(),
				attempt.getGradedAt(),
				attempt.getFeedback(),
				revealCorrectness,
				answerDtos,
				attempt.getCreatedAt(),
				attempt.getUpdatedAt());
	}

	/**
	 * Convenience: build the response with no parent quiz
	 * reference (e.g. when only the attempt is in scope).
	 */
	public AttemptResponse toResponse(QuizAttempt attempt,
			boolean revealCorrectness, int pendingAnswerCount) {
		return toResponse(attempt, attempt.getQuiz(),
				revealCorrectness, pendingAnswerCount);
	}

	// ------------------------------------------------------------------
	// Attempt → AttemptSummary
	// ------------------------------------------------------------------

	/**
	 * Build a lightweight {@link AttemptSummary} for the TEACHER
	 * listing and grading queue. {@code pendingAnswerCount} is
	 * computed upstream and passed in.
	 */
	public AttemptSummary toSummary(QuizAttempt attempt, Quiz quiz,
			int pendingAnswerCount) {
		return new AttemptSummary(
				attempt.getPublicUuid(),
				quiz != null ? quiz.getPublicUuid() : null,
				attempt.getStudentUserId(),
				attempt.getAttemptNumber(),
				attempt.getStatus(),
				toInt(attempt.getAutoScore()),
				toInt(attempt.getManualScore()),
				toInt(attempt.getScore()),
				quiz != null ? toInt(quiz.getMaxScore()) : null,
				pendingAnswerCount,
				attempt.getStartedAt(),
				attempt.getSubmittedAt(),
				attempt.getGradedAt(),
				attempt.getCreatedAt());
	}

	// ------------------------------------------------------------------
	// Answer → AnswerResponse
	// ------------------------------------------------------------------

	private AnswerResponse toAnswerResponse(QuizAnswer a,
			boolean revealCorrectness) {
		if (!revealCorrectness) {
			return new AnswerResponse(
					a.getPublicUuid(),
					a.getQuestion() != null
							? a.getQuestion().getPublicUuid() : null,
					a.getSelectedOptionId(),
					a.getSelectedBoolean(),
					a.getTextAnswer(),
					null, null, null, null,
					a.getUpdatedAt());
		}
		return new AnswerResponse(
				a.getPublicUuid(),
				a.getQuestion() != null
						? a.getQuestion().getPublicUuid() : null,
				a.getSelectedOptionId(),
				a.getSelectedBoolean(),
				a.getTextAnswer(),
				a.getCorrect(),
				toInt(a.getPointsAwarded()),
				a.getGradedByUserId(),
				a.getGradedAt(),
				a.getUpdatedAt());
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private static Integer toInt(Short s) {
		return s == null ? null : s.intValue();
	}

	/**
	 * Time remaining in seconds, capped at 0. Returns null when
	 * the attempt has no time limit ({@code expiresAt} null).
	 */
	private static Integer computeTimeRemaining(QuizAttempt attempt) {
		Instant expiresAt = attempt.getExpiresAt();
		if (expiresAt == null) {
			return null;
		}
		long diff = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
		return (int) Math.max(0, diff);
	}
}
