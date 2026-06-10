package com.edushift.modules.evaluations.rubric.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Per-criterion, per-level description used in a {@code Rubric}.
 *
 * <p>The {@code level} code must match a level in the rubric's
 * {@code levels[]} array (validated by the service layer). The
 * canonical MINEDU codes are the {@code RubricLevel} enum names
 * ({@code EN_INICIO}, {@code EN_PROCESO}, {@code ESPERADO},
 * {@code SOBRESALIENTE}); tenants can also define custom level codes
 * as long as they are unique within the rubric.</p>
 */
public record DescriptorInput(
		@NotBlank
		@Size(max = 64)
		String level,

		@NotBlank
		@Size(max = 2000)
		String text
) {
}
