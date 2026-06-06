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
		UUID coursePublicUuid,
		String courseCode,
		String courseName,
		UUID academicPeriodPublicUuid,
		PeriodType periodType,
		Integer periodOrdinal,
		Instant assignedAt,
		Instant unassignedAt,
		boolean active
) {
}
