package com.edushift.modules.students.enrollments.dto;

import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.students.enrollments.entity.StudentEnrollment}.
 *
 * <p>Includes denormalised labels for student / section / year so list
 * and detail UIs render with a single fetch. The {@code active} flag is
 * derived from {@code status == ACTIVE} for client convenience.</p>
 */
public record EnrollmentResponse(
		UUID publicUuid,

		UUID studentPublicUuid,
		String studentFullName,
		String studentDocumentNumber,

		UUID sectionPublicUuid,
		String sectionName,

		UUID academicYearPublicUuid,
		String academicYearName,

		LocalDate enrolledAt,
		LocalDate withdrawnAt,
		StudentEnrollmentStatus status,
		boolean active,
		String notes,

		Instant createdAt,
		Instant updatedAt
) {
}
