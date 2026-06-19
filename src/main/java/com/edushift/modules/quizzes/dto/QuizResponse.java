package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.QuizStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of a {@link com.edushift.modules.quizzes.entity.Quiz}
 * with its questions and options nested (Sprint 7b / BE-7b.1).
 *
 * <p>This is the response shape for {@code GET /quizzes/{uuid}}
 * and for the create/publish actions. Use {@link QuizSummary} for
 * list endpoints.
 *
 * <p>Includes a {@code revealCorrectness} flag (populated by the
 * service from the caller's role) so the FE can render the
 * correct/incorrect state on quiz-builder screens while keeping
 * taker responses private in 7b.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizResponse(
		UUID publicUuid,
		UUID sectionPublicUuid,
		String title,
		String description,
		QuizStatus status,
		Instant dueAt,
		Integer timeLimitMinutes,
		Integer maxAttempts,
		Integer maxScore,
		UUID ownerPublicUuid,
		Instant publishedAt,
		Instant closedAt,
		/**
		 * Attached rubric public UUID (BE-7b.3). NULL when the
		 * quiz is graded numerically only.
		 */
		UUID rubricPublicUuid,
		/**
		 * Derived evaluation public UUID that anchors the
		 * rubric-anchored {@code grade_records} entries (BE-7b.3).
		 * NULL when no rubric is attached.
		 */
		UUID rubricEvaluationPublicUuid,
		int questionCount,
		int totalPoints,
		boolean revealCorrectness,
		List<QuestionResponse> questions,
		Instant createdAt,
		Instant updatedAt
) {
}
