package com.edushift.modules.academic.competency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Capacity nested in {@link CreateCompetencyRequest} so a teacher can
 * create the competency and its capacities in one call.
 *
 * <p>{@code code} is optional. When omitted, the service derives it
 * from {@code name} and keeps it unique inside the new competency.</p>
 */
public record CreateCompetencyCapacityRequest(

		@Size(max = 40, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 300, message = "name length out of range")
		String name
) {
}
