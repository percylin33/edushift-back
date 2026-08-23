package com.edushift.modules.academic.section.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/academic/sections}.
 */
public record CreateSectionRequest(

		@NotNull(message = "academicYearPublicUuid is required")
		UUID academicYearPublicUuid,

		@NotNull(message = "gradePublicUuid is required")
		UUID gradePublicUuid,

		@NotBlank(message = "name is required")
		@Size(min = 1, max = 40, message = "name length out of range")
		String name,

		@Positive(message = "capacity must be >= 1")
		Integer capacity,

		@Positive(message = "displayOrder must be >= 1")
		Integer displayOrder,

		UUID homeroomTeacherPublicUuid
) {
}
