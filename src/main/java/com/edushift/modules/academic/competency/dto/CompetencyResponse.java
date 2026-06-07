package com.edushift.modules.academic.competency.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.competency.entity.Competency}
 * with the parent course summary + the embedded list of capacities (sorted
 * by displayOrder asc) so the FE can render the detail view without a
 * second fetch.
 */
public record CompetencyResponse(
		UUID publicUuid,
		CourseRef course,
		String code,
		String name,
		String description,
		Integer displayOrder,
		Boolean isActive,
		List<CapacityRef> capacities,
		Instant createdAt,
		Instant updatedAt
) {

	public record CourseRef(
			UUID publicUuid,
			String code,
			String name
	) {
	}

	public record CapacityRef(
			UUID publicUuid,
			String code,
			String name,
			Integer displayOrder,
			Boolean isActive
	) {
	}
}
