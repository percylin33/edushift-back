package com.edushift.modules.academic.section.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.section.entity.Section}.
 *
 * <p>Includes denormalised parent labels ({@code academicYearName},
 * {@code gradeName}, {@code levelCode}) so the FE can render lists and
 * detail views without a second round-trip.</p>
 */
public record SectionResponse(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		String academicYearStatus,
		UUID gradePublicUuid,
		String gradeName,
		Integer gradeOrdinal,
		UUID levelPublicUuid,
		String levelCode,
		String levelName,
		String name,
		Integer capacity,
		Integer displayOrder,
		Instant createdAt,
		Instant updatedAt
) {
}
