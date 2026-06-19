package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One answer entry in {@link SaveAnswersRequest}
 * (Sprint 7b / BE-7b.2). The {@code questionType} is required so
 * the service can pick the correct field to persist (the FE sends
 * it as a hint, but the server re-reads the question row to avoid
 * trusting the client on type).
 *
 * <p>Mutually exclusive fields (DB CHECK enforced): one of
 * {@code selectedOptionId}, {@code selectedBoolean} or
 * {@code textAnswer} must be non-null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerInput(
		@NotNull UUID questionPublicUuid,
		@NotNull QuestionType questionType,
		UUID selectedOptionId,
		Boolean selectedBoolean,
		@Size(max = 5000) String textAnswer
) {
}
