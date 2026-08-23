package com.edushift.modules.academic.levelgrade.dto;

import com.edushift.modules.academic.levelgrade.entity.TeachingMode;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code PUT /v1/academic/levels/{levelUuid}/grades/{gradeUuid}}.
 */
public record UpdateGradeRequest(

		@Size(min = 1, max = 100, message = "name length out of range")
		String name,

		@Positive(message = "ordinal must be >= 1")
		Integer ordinal,

		TeachingMode teachingMode
) {

	public boolean isEmpty() {
		return name == null && ordinal == null && teachingMode == null;
	}
}
