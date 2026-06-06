package com.edushift.modules.academic.course.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.course.entity.Course}
 * with the full list of associated levels (sorted by ordinal asc).
 *
 * <p>Levels are flat {@link CourseLevelRef} objects so the FE table can
 * render the chips column with a single fetch.</p>
 */
public record CourseResponse(
		UUID publicUuid,
		String code,
		String name,
		String description,
		Integer credits,
		Integer hoursPerWeek,
		Boolean isActive,
		List<CourseLevelRef> levels,
		Instant createdAt,
		Instant updatedAt
) {

	public record CourseLevelRef(
			UUID publicUuid,
			String code,
			String name,
			Integer ordinal
	) {
	}
}
