package com.edushift.modules.academic.section.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload of {@code PUT /v1/academic/sections/{publicUuid}}.
 *
 * <p>Pass {@code clearHomeroom=true} to remove the tutor without a PATCH
 * null-semantics endpoint.</p>
 */
public record UpdateSectionRequest(

		@Size(min = 1, max = 40, message = "name length out of range")
		String name,

		@Positive(message = "capacity must be >= 1")
		Integer capacity,

		@Positive(message = "displayOrder must be >= 1")
		Integer displayOrder,

		UUID homeroomTeacherPublicUuid,

		Boolean clearHomeroom
) {

	public boolean isEmpty() {
		return name == null && capacity == null && displayOrder == null
				&& homeroomTeacherPublicUuid == null
				&& !Boolean.TRUE.equals(clearHomeroom);
	}
}
