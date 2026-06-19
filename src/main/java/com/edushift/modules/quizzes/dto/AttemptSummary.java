package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of a {@code lms_quiz_attempts} row, used
 * in TEACHER-side listings ({@code GET /quizzes/{uuid}/attempts})
 * and in the grading queue (Sprint 7b / BE-7b.2).
 *
 * <p>Carries the student id and the attempt metadata; full
 * answers come from {@code GET /attempts/{uuid}} when the teacher
 * opens the grading detail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptSummary(
		UUID publicUuid,
		UUID quizPublicUuid,
		UUID studentUserId,
		Short attemptNumber,
		AttemptStatus status,
		Integer autoScore,
		Integer manualScore,
		Integer score,
		Integer maxScore,
		int pendingAnswerCount,
		Instant startedAt,
		Instant submittedAt,
		Instant gradedAt,
		Instant createdAt
) {
}
