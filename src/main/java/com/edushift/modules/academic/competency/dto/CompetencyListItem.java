package com.edushift.modules.academic.competency.dto;

import java.util.UUID;

/**
 * Lean projection used by {@code GET /v1/academic/courses/{courseUuid}/competencies}.
 *
 * <p>Returns the {@code capacityCount} so the FE can render the "N
 * capacidades" chip without a second fetch.</p>
 */
public record CompetencyListItem(
		UUID publicUuid,
		String code,
		String name,
		Integer displayOrder,
		Boolean isActive,
		Long capacityCount
) {
}
