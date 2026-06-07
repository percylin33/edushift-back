package com.edushift.modules.academic.unit.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.unit.entity.Unit}
 * with the parent course summary so the FE can render breadcrumbs without
 * a second fetch.
 */
public record UnitResponse(
		UUID publicUuid,
		CourseRef course,
		String name,
		String description,
		Integer displayOrder,
		LocalDate startDate,
		LocalDate endDate,
		Boolean isActive,
		Long sessionCount,
		Instant createdAt,
		Instant updatedAt
) {

	public record CourseRef(
			UUID publicUuid,
			String code,
			String name
	) {
	}
}
