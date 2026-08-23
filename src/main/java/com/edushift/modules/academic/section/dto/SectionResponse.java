package com.edushift.modules.academic.section.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.section.entity.Section}.
 */
public record SectionResponse(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		String academicYearStatus,
		UUID gradePublicUuid,
		String gradeName,
		Integer gradeOrdinal,
		String teachingMode,
		UUID levelPublicUuid,
		String levelCode,
		String levelName,
		String name,
		Integer capacity,
		Integer displayOrder,
		UUID homeroomTeacherPublicUuid,
		String homeroomTeacherName,
		Instant createdAt,
		Instant updatedAt
) {
}
