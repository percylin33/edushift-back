package com.edushift.modules.teachers.assignments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/teachers/{teacherUuid}/assignments}.
 *
 * <p>The {@code teacher} is the path variable; the remaining three
 * UUIDs anchor the assignment to a {@code (section, course, period)}
 * tuple. The service performs four lookups and validates consistency
 * (year matches, level applies, teacher is assignable) before saving.</p>
 */
public record CreateAssignmentRequest(

		@NotNull(message = "sectionPublicUuid is required")
		UUID sectionPublicUuid,

		@NotNull(message = "coursePublicUuid is required")
		UUID coursePublicUuid,

		@NotNull(message = "academicPeriodPublicUuid is required")
		UUID academicPeriodPublicUuid,

		@Size(max = 1000, message = "notes too long")
		String notes
) {
}
