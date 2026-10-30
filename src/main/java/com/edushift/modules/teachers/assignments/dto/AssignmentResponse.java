package com.edushift.modules.teachers.assignments.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.teachers.assignments.entity.TeacherAssignment}.
 *
 * <p>Includes denormalised labels for teacher / section / course / period
 * so list and detail UIs render with a single fetch.</p>
 */
public record AssignmentResponse(
		UUID publicUuid,

		UUID teacherPublicUuid,
		String teacherFullName,

		UUID sectionPublicUuid,
		String sectionName,
		/** Denormalised grade label (e.g. {@code "1ro Primaria"}); null-safe. */
		String gradeName,

		UUID coursePublicUuid,
		String courseCode,
		String courseName,
		/** Optional course hex color {@code #RRGGBB}; null → FE resolves by code. */
		String courseColor,
		/** Optional course Lucide icon key; null → FE resolves by code. */
		String courseIcon,

		UUID academicPeriodPublicUuid,
		PeriodType periodType,
		Integer periodOrdinal,
		String periodName,

		UUID academicYearPublicUuid,
		String academicYearName,

		Instant assignedAt,
		Instant unassignedAt,
		boolean active,
		String notes,

		Instant createdAt,
		Instant updatedAt
) {
}
