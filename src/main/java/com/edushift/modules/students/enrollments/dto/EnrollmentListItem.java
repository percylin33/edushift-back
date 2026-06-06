package com.edushift.modules.students.enrollments.dto;

import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lean list-row projection for {@code GET /v1/students/{uuid}/enrollments}.
 *
 * <p>Drops audit timestamps so the response stays small for long
 * histories. The full {@link EnrollmentResponse} is reserved for the
 * detail / create endpoints.</p>
 */
public record EnrollmentListItem(
		UUID publicUuid,

		UUID studentPublicUuid,
		String studentFullName,

		UUID sectionPublicUuid,
		String sectionName,

		UUID academicYearPublicUuid,
		String academicYearName,

		LocalDate enrolledAt,
		LocalDate withdrawnAt,
		StudentEnrollmentStatus status,
		boolean active
) {
}
