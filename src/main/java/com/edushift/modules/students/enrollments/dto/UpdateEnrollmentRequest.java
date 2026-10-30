package com.edushift.modules.students.enrollments.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Payload of {@code PATCH /v1/enrollments/{publicUuid}}.
 *
 * <p>Updates {@code enrolledAt} on an ACTIVE enrollment (must fall inside
 * the academic year window). Used so admins can align section placement
 * date with grading ({@code GRADE_STUDENT_NOT_ENROLLED} gate).</p>
 */
public record UpdateEnrollmentRequest(

		@NotNull(message = "enrolledAt is required")
		LocalDate enrolledAt
) {
}
