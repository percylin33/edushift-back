package com.edushift.modules.quizzes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Full projection of a {@link com.edushift.modules.quizzes.entity.QuizOption}
 * (Sprint 7b / BE-7b.1).
 *
 * <p>{@code isCorrect} and {@code explanation} are only returned
 * to users with grading authority ({@code LMS_QUIZ_GRADE}); the
 * service/mapper must drop them for taker responses (FE-7b.2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OptionResponse(
		UUID publicUuid,
		String label,
		Boolean isCorrect,
		String explanation,
		int position
) {
}
