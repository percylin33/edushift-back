package com.edushift.modules.academic.levelgrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code POST /v1/academic/levels/{levelUuid}/grades}.
 *
 * <p>{@code levelUuid} is taken from the path; the body only carries
 * {@code name} and {@code ordinal}.</p>
 */
public record CreateGradeRequest(

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 100, message = "name length out of range")
		String name,

		@NotNull(message = "ordinal is required")
		@Positive(message = "ordinal must be >= 1")
		Integer ordinal
) {
}
