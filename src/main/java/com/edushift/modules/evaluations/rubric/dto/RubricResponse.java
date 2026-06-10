package com.edushift.modules.evaluations.rubric.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of a {@code Rubric} returned by
 * {@code GET /v1/academic/rubrics/{uuid}} and the create / fork / update
 * endpoints.
 *
 * <p>Includes the full criteria (with descriptors) and levels arrays,
 * the {@code isSystem} flag (read-only flag in the UI), and
 * {@code parentRubricPublicUuid} (non-null only on forks).</p>
 */
public record RubricResponse(
		UUID publicUuid,
		String name,
		String description,
		List<CriterionView> criteria,
		List<LevelView> levels,
		Boolean isSystem,
		UUID parentRubricPublicUuid,
		Boolean isActive,
		Instant createdAt,
		Instant updatedAt
) {
}
