package com.edushift.modules.teachers.assignments.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.Instant;
import java.util.UUID;

/**
 * Reverse-view projection used by {@code GET /v1/sections/{uuid}/teachers}:
 * given a section and (optional) period, list the teachers and the
 * course they teach in that section. One row per
 * {@code (teacher, course, period)} active assignment.
 */
public record SectionTeacherItem(
		UUID assignmentPublicUuid,
		UUID teacherPublicUuid,
		String teacherFullName,
		String teacherEmail,
		UUID coursePublicUuid,
		String courseCode,
		String courseName,
		UUID academicPeriodPublicUuid,
		PeriodType periodType,
		Integer periodOrdinal,
		Instant assignedAt
) {
}
