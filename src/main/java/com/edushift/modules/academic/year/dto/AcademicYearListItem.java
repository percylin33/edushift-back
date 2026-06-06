package com.edushift.modules.academic.year.dto;

import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lean projection used by {@code GET /v1/academic/years} (list).
 * Drops audit timestamps to keep the payload tight; the detail endpoint
 * returns {@link AcademicYearResponse} when needed.
 */
public record AcademicYearListItem(
		UUID publicUuid,
		String name,
		AcademicYearStatus status,
		LocalDate startDate,
		LocalDate endDate
) {
}
