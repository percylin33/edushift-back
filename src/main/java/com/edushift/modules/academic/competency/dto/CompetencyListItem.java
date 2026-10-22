package com.edushift.modules.academic.competency.dto;

import com.edushift.modules.academic.competency.entity.CompetencyOrigin;
import com.edushift.modules.academic.competency.entity.CurriculumSourceContentStatus;
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
		Long capacityCount,
		CompetencyOrigin origin,
		UUID createdBy,
		String sourceCatalogVersion,
		String sourceCatalogCode,
		String sourceLevelCode,
		String sourceCycleCodes,
		CurriculumSourceContentStatus sourceContentStatus
) {
	public CompetencyListItem(UUID publicUuid, String code, String name,
			Integer displayOrder, Boolean isActive, Long capacityCount,
			CompetencyOrigin origin, UUID createdBy) {
		this(publicUuid, code, name, displayOrder, isActive, capacityCount, origin, createdBy,
				null, null, null, null, null);
	}
}
