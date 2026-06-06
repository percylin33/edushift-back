package com.edushift.modules.academic.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/academic/courses}.
 *
 * <p>{@code levelPublicUuids} MUST contain at least one UUID — courses
 * apply to >= 1 level (invariant {@code COURSE_NEEDS_AT_LEAST_ONE_LEVEL},
 * 422). Cross-tenant level UUIDs become a 404 {@code RESOURCE_NOT_FOUND}
 * (anti-enumeration).</p>
 */
public record CreateCourseRequest(

		@NotBlank(message = "code is required")
		@Size(min = 1, max = 30, message = "code length out of range")
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
				message = "code must start with a letter and contain only letters, digits, or underscores")
		String code,

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 200, message = "name length out of range")
		String name,

		@Size(max = 4000, message = "description too long")
		String description,

		@PositiveOrZero(message = "credits must be >= 0")
		Integer credits,

		@PositiveOrZero(message = "hoursPerWeek must be >= 0")
		Integer hoursPerWeek,

		Boolean isActive,

		@NotEmpty(message = "levelPublicUuids must contain at least one level")
		List<UUID> levelPublicUuids
) {
}
