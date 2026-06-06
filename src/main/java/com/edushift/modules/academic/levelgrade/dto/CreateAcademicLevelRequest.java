package com.edushift.modules.academic.levelgrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code POST /v1/academic/levels}.
 *
 * <p>{@code code} is normalised to uppercase by the entity at persist
 * time; the regex below enforces the allowed alphabet at the API edge
 * so clients get a clean 400 instead of a 409 from the DB CHECK.</p>
 */
public record CreateAcademicLevelRequest(

		@NotBlank(message = "code is required")
		@Size(min = 1, max = 40, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 100, message = "name length out of range")
		String name,

		@NotNull(message = "ordinal is required")
		@Positive(message = "ordinal must be >= 1")
		Integer ordinal
) {
}
