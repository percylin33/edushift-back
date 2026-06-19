package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * JSON body for {@code PATCH /quizzes/{uuid}} (Sprint 7b / BE-7b.1).
 * All fields nullable so PATCH is partial. Empty PATCH triggers
 * {@code QUIZ_RECORD_EMPTY_PATCH}.
 *
 * <p>Editing is only allowed in DRAFT state. Once a quiz is
 * PUBLISHED, only {@code dueAt} and {@code maxAttempts} may be
 * patched; the rest is rejected with {@code QUIZ_NOT_DRAFT}.
 */
public record UpdateQuizRequest(
		@Size(max = 200) String title,
		@Size(max = 10000) String description,
		@Future Instant dueAt,
		@Min(1) @Max(480) Integer timeLimitMinutes,
		@Min(1) @Max(10) Integer maxAttempts,
		@Min(0) @Max(1000) Integer maxScore
) {
}
