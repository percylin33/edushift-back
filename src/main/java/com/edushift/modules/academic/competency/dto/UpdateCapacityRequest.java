package com.edushift.modules.academic.competency.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code PUT /v1/academic/capacities/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. To change the
 * relative order use {@code PATCH /v1/academic/competencies/{competencyUuid}/capacities/reorder}
 * (see {@link CapacityReorderRequest}).</p>
 */
public record UpdateCapacityRequest(

		@Size(min = 1, max = 40, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@Size(min = 1, max = 300, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		Boolean isActive
) {

	public boolean isEmpty() {
		return code == null
				&& name == null
				&& description == null
				&& isActive == null;
	}
}
