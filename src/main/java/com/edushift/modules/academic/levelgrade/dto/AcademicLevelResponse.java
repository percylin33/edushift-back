package com.edushift.modules.academic.levelgrade.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.levelgrade.entity.AcademicLevel}.
 *
 * <p>Includes the embedded list of grades (sorted by ordinal asc) so the
 * UI can render the catalog with a single round-trip
 * ({@code GET /v1/academic/levels} returns 3 levels and ~14 grades —
 * tiny payload).</p>
 */
public record AcademicLevelResponse(
		UUID publicUuid,
		String code,
		String name,
		Integer ordinal,
		List<GradeResponse> grades,
		Instant createdAt,
		Instant updatedAt
) {
}
