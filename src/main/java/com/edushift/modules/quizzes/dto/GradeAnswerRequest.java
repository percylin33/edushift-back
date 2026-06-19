package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * JSON body for
 * {@code PATCH /quizzes/{quizUuid}/attempts/{attemptUuid}/answers/{answerUuid}}
 * (Sprint 7b / BE-7b.1).
 *
 * <p>Used by teachers to override the auto-graded points for an
 * answer (typically for MC/TF where the student selected the
 * right option but the teacher wants to apply a partial-credit
 * adjustment, or for SHORT_ANSWER manual grading).
 *
 * <p>{@code pointsAwarded} must be in {@code [0, 1000]}; the
 * service re-checks it against the question's own {@code points}
 * upper bound (a question with {@code points=5} can never be
 * awarded 100).
 *
 * <p>Retained for backward compatibility with the BE-7b.1 stub;
 * the canonical record is now {@link ManualGradeAnswerRequest}
 * (BE-7b.2) which carries an optional
 * {@code answerPublicUuid} for the bulk grading path. Both
 * shapes are accepted by the
 * {@code PATCH .../answers/{answerUuid}} endpoint.
 */
public record GradeAnswerRequest(
		@NotNull @Min(0) @Max(1000) Integer pointsAwarded
) {
}
