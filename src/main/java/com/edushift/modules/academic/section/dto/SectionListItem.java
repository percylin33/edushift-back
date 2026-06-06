package com.edushift.modules.academic.section.dto;

import java.util.UUID;

/**
 * Lean projection used by {@code GET /v1/academic/sections}. Skips audit
 * timestamps and over-eager nested fields. Same denormalised labels as
 * {@link SectionResponse} so the table FE renders with a single fetch.
 */
public record SectionListItem(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		String academicYearStatus,
		UUID gradePublicUuid,
		String gradeName,
		Integer gradeOrdinal,
		UUID levelPublicUuid,
		String levelCode,
		String name,
		Integer capacity,
		Integer displayOrder
) {
}
