package com.edushift.modules.academic.competency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code POST /v1/academic/competencies/{competencyUuid}/capacities}.
 *
 * <p>Validation rules mirrored at the service layer:</p>
 * <ul>
 *   <li>{@code code} matches {@code ^[A-Z][A-Z0-9_]*$} (auto-uppercased)
 *       and is unique inside the competency case-insensitively
 *       ({@code CAPACITY_CODE_TAKEN}, 409).</li>
 *   <li>If {@code displayOrder} is omitted, the service appends to the
 *       tail (max(displayOrder) + 1).</li>
 * </ul>
 */
public record CreateCapacityRequest(

		@NotBlank(message = "code is required")
		@Size(min = 1, max = 40, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 300, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		@Positive(message = "displayOrder must be >= 1")
		Integer displayOrder,

		Boolean isActive
) {
}
