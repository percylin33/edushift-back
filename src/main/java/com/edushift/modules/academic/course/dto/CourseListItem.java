package com.edushift.modules.academic.course.dto;

import java.util.List;
import java.util.UUID;

/**
 * Lean projection used by {@code GET /v1/academic/courses}. Keeps the
 * level chips so the table renders without a second fetch.
 */
public record CourseListItem(
		UUID publicUuid,
		String code,
		String name,
		Integer credits,
		Integer hoursPerWeek,
		Boolean isActive,
		List<CourseResponse.CourseLevelRef> levels
) {
}
