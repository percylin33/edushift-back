package com.edushift.modules.academic.unit.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lean projection used by {@code GET /v1/academic/courses/{courseUuid}/units}.
 *
 * <p>Returns enough metadata to render the sortable table (name, dates,
 * order, active flag) plus the {@code sessionCount} so the FE can show
 * the "N sesiones" chip without a second fetch.</p>
 */
public record UnitListItem(
		UUID publicUuid,
		String name,
		Integer displayOrder,
		LocalDate startDate,
		LocalDate endDate,
		Boolean isActive,
		Long sessionCount
) {
}
