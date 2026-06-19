package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * JSON body for {@code POST /quizzes/{uuid}/questions}
 * (Sprint 7b / BE-7b.1).
 *
 * <h3>Per-type payload</h3>
 * <ul>
 *   <li>{@code MC}: {@code options} is required (2-6), and exactly
 *       one option must have {@code isCorrect=true}.</li>
 *   <li>{@code TF}: {@code options} must be null/empty;
 *       {@code correctBoolean} is required.</li>
 *   <li>{@code SHORT_ANSWER}: {@code options} must be null/empty;
 *       {@code correctText} or {@code expectedKeywords} are
 *       recommended. {@code expectedKeywords} is a
 *       comma-separated string parsed into a {@code text[]}
 *       column (DEBT-BE-7B-3 covers richer fuzzy matching).</li>
 * </ul>
 */
public record CreateQuestionRequest(
		@NotNull QuestionType type,
		@NotBlank @Size(max = 2000) String prompt,
		@NotNull @Min(1) @Max(100) Integer points,
		@Min(1) @Max(200) Integer position,
		@Size(max = 4000) String correctText,
		@Size(max = 1000) String expectedKeywords,
		Boolean correctBoolean,
		List<CreateOptionRequest> options
) {
}
