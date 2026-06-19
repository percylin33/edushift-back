package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of a {@link com.edushift.modules.quizzes.entity.QuizQuestion}
 * with its options nested (Sprint 7b / BE-7b.1).
 *
 * <p>The {@code correctText} / {@code correctBoolean} /
 * {@code expectedKeywords} fields are only returned to users
 * with grading authority ({@code LMS_QUIZ_GRADE}); the
 * service/mapper drops them for taker responses (FE-7b.2).
 *
 * <p>{@code expectedKeywords} is sent as a comma-separated
 * string for API symmetry; the underlying column is
 * {@code text[]}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionResponse(
		UUID publicUuid,
		QuestionType type,
		String prompt,
		int points,
		int position,
		String correctText,
		String expectedKeywords,
		Boolean correctBoolean,
		List<OptionResponse> options
) {
}
