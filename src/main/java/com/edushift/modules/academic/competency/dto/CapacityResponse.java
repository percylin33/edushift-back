package com.edushift.modules.academic.competency.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.competency.entity.Capacity}
 * with the parent competency + course refs so the FE can render
 * breadcrumbs (Course → Competency → Capacity) without extra fetches.
 *
 * <p>Used both for {@code GET /capacities/{id}} (detail) and the listing
 * {@code GET /competencies/{id}/capacities} (no separate ListItem because
 * the projection is already lean).</p>
 */
public record CapacityResponse(
		UUID publicUuid,
		CompetencyRef competency,
		String code,
		String name,
		String description,
		Integer displayOrder,
		Boolean isActive,
		Instant createdAt,
		Instant updatedAt
) {

	public record CompetencyRef(
			UUID publicUuid,
			String code,
			String name,
			CourseRef course
	) {
	}

	public record CourseRef(
			UUID publicUuid,
			String code,
			String name
	) {
	}
}
