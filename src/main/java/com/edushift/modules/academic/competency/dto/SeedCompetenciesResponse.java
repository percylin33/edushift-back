package com.edushift.modules.academic.competency.dto;

import java.util.List;

/**
 * Payload returned by {@code POST /v1/academic/courses/{courseUuid}/competencies/seed-defaults}.
 *
 * <p>Reports how many competencies / capacities were created. {@code seeded=false}
 * means the course already had at least one competency and the seed was skipped
 * (idempotent contract). {@code unsupportedCourseCode=true} means the course's
 * {@code code} is not recognised by {@code CompetencyDefaults} (currently
 * {@code MAT} and {@code COMU}); the operation is a no-op in that case so
 * the FE can show an "unsupported" hint.</p>
 */
public record SeedCompetenciesResponse(
		boolean seeded,
		boolean unsupportedCourseCode,
		String courseCode,
		int competenciesCreated,
		int capacitiesCreated,
		List<CompetencyListItem> created
) {
}
