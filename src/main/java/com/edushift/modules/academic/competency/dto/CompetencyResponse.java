package com.edushift.modules.academic.competency.dto;

import com.edushift.modules.academic.competency.entity.CompetencyOrigin;
import com.edushift.modules.academic.competency.entity.CurriculumSourceContentStatus;
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
		Instant updatedAt,
		CompetencyOrigin origin,
		UUID createdBy,
		String sourceCatalogVersion,
		String sourceCatalogCode,
		String sourceLevelCode,
		String sourceCycleCodes,
		CurriculumSourceContentStatus sourceContentStatus
) {
	/** Backward-compatible constructor for older API-focused tests and callers. */
	public CompetencyResponse(UUID publicUuid, CourseRef course, String code, String name,
			String description, Integer displayOrder, Boolean isActive, List<CapacityRef> capacities,
			Instant createdAt, Instant updatedAt, CompetencyOrigin origin, UUID createdBy) {
		this(publicUuid, course, code, name, description, displayOrder, isActive, capacities,
				createdAt, updatedAt, origin, createdBy, null, null, null, null, null);
	}

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
			Boolean isActive,
			String sourceCatalogVersion,
			String sourceCatalogCode,
			CurriculumSourceContentStatus sourceContentStatus
	) {
		public CapacityRef(UUID publicUuid, String code, String name,
				Integer displayOrder, Boolean isActive) {
			this(publicUuid, code, name, displayOrder, isActive, null, null, null);
		}
	}
}
