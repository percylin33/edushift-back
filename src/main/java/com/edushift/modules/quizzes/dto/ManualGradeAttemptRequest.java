package com.edushift.modules.quizzes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * JSON body for {@code POST /v1/lms/attempts/{uuid}/grade}
 * (Sprint 7b / BE-7b.2). The teacher submits the override for
 * each pending SHORT_ANSWER in one call.
 *
 * <p>Each entry's {@code pointsAwarded} is validated by the
 * service against the question's own {@code points} upper bound
 * (a question with {@code points=5} can never be awarded 100).
 *
 * <p>{@code feedback} is optional (max 2000 chars, matches the
 * DB column) and applies to the whole attempt; it becomes visible
 * to the student after the attempt transitions to
 * {@code GRADED}.
 */
public record ManualGradeAttemptRequest(
		@Valid List<ManualGradeAnswerRequest> grades,
		@Size(max = 2000) String feedback
) {
}
