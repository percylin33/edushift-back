package com.edushift.modules.teachers.assignments.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.Instant;
import java.util.UUID;

/**
 * Lean projection used by {@code GET /v1/teachers/{uuid}/assignments}.
 * Skips audit timestamps; same denormalised labels as
 * {@link AssignmentResponse} so the table FE renders with one round trip.
 */
public record AssignmentListItem(
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
		Instant assignedAt,
		Instant unassignedAt,
		boolean active
) {
}
