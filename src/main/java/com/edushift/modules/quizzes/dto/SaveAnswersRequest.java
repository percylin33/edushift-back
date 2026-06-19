package com.edushift.modules.quizzes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * JSON body for {@code PATCH /v1/lms/attempts/{uuid}} (autosave)
 * (Sprint 7b / BE-7b.2).
 *
 * <p>The list represents the <strong>current</strong> answer set for
 * the attempt: any question not in the list keeps its previously
 * saved value. New questions create new rows; repeated questions
 * update the existing row in-place (DB UNIQUE on
 * {@code (attempt_id, question_id)}).
 */
public record SaveAnswersRequest(
		@NotEmpty @Valid List<AnswerInput> answers
) {
}
