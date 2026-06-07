package com.edushift.modules.academic.unit.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload of {@code PUT /v1/academic/units/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. To change the
 * relative order use {@code PATCH /v1/academic/courses/{courseUuid}/units/reorder}
 * (see {@link UnitReorderRequest}); {@code displayOrder} is intentionally
 * NOT exposed here to avoid duplicate-ordinal races.</p>
 */
public record UpdateUnitRequest(

		@Size(min = 1, max = 200, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		LocalDate startDate,

		LocalDate endDate,

		Boolean isActive
) {

	public boolean isEmpty() {
		return name == null
				&& description == null
				&& startDate == null
				&& endDate == null
				&& isActive == null;
	}
}
