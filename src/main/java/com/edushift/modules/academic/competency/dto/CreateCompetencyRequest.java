package com.edushift.modules.academic.competency.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload of {@code POST /v1/academic/courses/{courseUuid}/competencies}.
 *
 * <p>Validation rules mirrored at the service layer:</p>
 * <ul>
 *   <li>{@code code} matches {@code ^[A-Z][A-Z0-9_]*$} (auto-uppercased)
 *       and is unique inside the course case-insensitively
 *       ({@code COMPETENCY_CODE_TAKEN}, 409).</li>
 *   <li>If {@code displayOrder} is omitted, the service appends to the
 *       tail (max(displayOrder) + 1).</li>
 *   <li>{@code code} is optional. When omitted, the service derives it
 *       from {@code name}. A teacher must send at least one capacity.</li>
 * </ul>
 */
public record CreateCompetencyRequest(

		@Size(max = 40, message = "code length out of range")
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

		Boolean isActive,

		@Valid
		List<@Valid CreateCompetencyCapacityRequest> capacities
) {
	@JsonCreator
	public CreateCompetencyRequest(String code, String name, String description,
			Integer displayOrder, Boolean isActive,
			List<CreateCompetencyCapacityRequest> capacities) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.displayOrder = displayOrder;
		this.isActive = isActive;
		this.capacities = capacities;
	}

	public CreateCompetencyRequest(String code, String name, String description,
			Integer displayOrder, Boolean isActive) {
		this(code, name, description, displayOrder, isActive, null);
	}
}
