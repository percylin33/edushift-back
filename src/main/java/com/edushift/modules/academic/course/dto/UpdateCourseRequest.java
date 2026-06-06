package com.edushift.modules.academic.course.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code PUT /v1/academic/courses/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. Use
 * {@code POST /courses/{uuid}/levels} to change the level associations
 * (see {@link UpdateCourseLevelsRequest}).</p>
 */
public record UpdateCourseRequest(

		@Size(min = 1, max = 30, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@Size(min = 1, max = 200, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		@PositiveOrZero(message = "credits must be >= 0")
		Integer credits,

		@PositiveOrZero(message = "hoursPerWeek must be >= 0")
		Integer hoursPerWeek,

		Boolean isActive
) {

	public boolean isEmpty() {
		return code == null
				&& name == null
				&& description == null
				&& credits == null
				&& hoursPerWeek == null
				&& isActive == null;
	}
}
