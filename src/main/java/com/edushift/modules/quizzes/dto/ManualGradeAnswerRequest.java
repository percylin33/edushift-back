package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Dual-use DTO (Sprint 7b / BE-7b.2).
 *
 * <p>Reused in two endpoints:
 * <ul>
 *   <li>{@code PATCH /v1/lms/quizzes/{quizUuid}/attempts/{attemptUuid}/answers/{answerUuid}}
 *       — single-answer override. The path carries the answer
 *       UUID, so {@code answerPublicUuid} is ignored in the
 *       request body (kept nullable for shape symmetry).</li>
 *   <li>Entry inside
 *       {@code POST /v1/lms/attempts/{uuid}/grade} — bulk
 *       grading; each entry carries its own
 *       {@code answerPublicUuid} so the server can identify
 *       the row to update.</li>
 * </ul>
 *
 * <p>Replaces the stub added in BE-7b.1 (which only had
 * {@code pointsAwarded}). The service validates the range
 * against the question's own {@code points} upper bound.
 */
public record ManualGradeAnswerRequest(
		UUID answerPublicUuid,
		@NotNull @Min(0) @Max(1000) Integer pointsAwarded
) {
}
