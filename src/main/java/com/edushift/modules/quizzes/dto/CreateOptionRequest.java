package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body field for a multiple-choice option
 * (Sprint 7b / BE-7b.1).
 *
 * <p>Used inside {@link CreateQuestionRequest#options()} or
 * inside {@link AddOptionRequest}.
 */
public record CreateOptionRequest(
		@NotBlank @Size(max = 500) String label,
		@NotNull Boolean isCorrect,
		@Size(max = 1000) String explanation
) {
}
