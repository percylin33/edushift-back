package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of a {@code lms_quiz_attempts} row with its
 * answers nested (Sprint 7b / BE-7b.2).
 *
 * <p>{@code revealCorrectness} is set by the service based on the
 * caller's authority (a STUDENT only sees it after their attempt
 * has been {@code GRADED}; a TEACHER with grading authority always
 * sees it for the grading queue).
 *
 * <p>The {@code timeRemainingSeconds} field is computed at the
 * service layer from {@code expiresAt} (set when the attempt was
 * started, only if the quiz has a time limit). For the player UI
 * the FE polls this field via {@code GET /attempts/{uuid}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptResponse(
		UUID publicUuid,
		UUID quizPublicUuid,
		UUID studentUserId,
		UUID submitterUserId,
		Short attemptNumber,
		AttemptStatus status,
		Instant startedAt,
		Instant submittedAt,
		Instant expiresAt,
		Integer timeRemainingSeconds,
		Integer autoScore,
		Integer manualScore,
		Integer score,
		Integer maxScore,
		UUID gradedByUserId,
		Instant gradedAt,
		String feedback,
		boolean revealCorrectness,
		List<AnswerResponse> answers,
		Instant createdAt,
		Instant updatedAt
) {
}
