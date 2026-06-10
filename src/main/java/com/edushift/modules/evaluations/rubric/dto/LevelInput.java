package com.edushift.modules.evaluations.rubric.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Input for a single achievement level in a {@code Rubric}.
 *
 * <p>The service layer enforces uniqueness of {@code code} within the
 * array and 2..4 items total. For canonical MINEDU rubrics the
 * {@code code} is one of the {@code RubricLevel} enum names; tenants
 * can use custom codes as long as they are unique.</p>
 */
public record LevelInput(
		@NotBlank
		@Size(max = 64)
		String code,

		@NotBlank
		@Size(max = 120)
		String name,

		@PositiveOrZero
		Integer order
) {
}
