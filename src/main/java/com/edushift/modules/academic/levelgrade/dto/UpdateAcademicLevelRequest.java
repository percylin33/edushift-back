package com.edushift.modules.academic.levelgrade.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code PUT /v1/academic/levels/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored.</p>
 */
public record UpdateAcademicLevelRequest(

		@Size(min = 1, max = 40, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@Size(min = 1, max = 100, message = "name length out of range")
		String name,

		@Positive(message = "ordinal must be >= 1")
		Integer ordinal
) {

	public boolean isEmpty() {
		return code == null && name == null && ordinal == null;
	}
}
