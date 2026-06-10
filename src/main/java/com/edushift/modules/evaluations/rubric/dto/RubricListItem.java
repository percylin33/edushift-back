package com.edushift.modules.evaluations.rubric.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lean projection used by
 * {@code GET /v1/academic/rubrics} and
 * {@code GET /v1/academic/rubrics/system}.
 *
 * <p>Includes the criterion names + weights (so the FE can render a
 * "20% redacción, 30% análisis, ..." summary line) but omits the
 * descriptors, keeping the payload small for the listing view.</p>
 */
public record RubricListItem(
		UUID publicUuid,
		String name,
		String description,
		Boolean isSystem,
		UUID parentRubricPublicUuid,
		Integer criterionCount,
		List<String> criterionSummary,
		Boolean isActive,
		Instant createdAt,
		Instant updatedAt
) {
}
