package com.edushift.modules.academic.unit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload of {@code POST /v1/academic/courses/{courseUuid}/units}.
 *
 * <p>Validation rules mirrored at the service layer:</p>
 * <ul>
 *   <li>{@code name} is unique inside the course case-insensitively
 *       ({@code UNIT_NAME_EXISTS}, 409).</li>
 *   <li>{@code endDate >= startDate} when both are present
 *       ({@code UNIT_DATE_INVERTED}, 400).</li>
 *   <li>If {@code displayOrder} is omitted, the service appends to the
 *       tail (max(displayOrder) + 1).</li>
 * </ul>
 */
public record CreateUnitRequest(

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 200, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		@Positive(message = "displayOrder must be >= 1")
		Integer displayOrder,

		LocalDate startDate,

		LocalDate endDate,

		Boolean isActive
) {
}
