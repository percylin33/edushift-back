package com.edushift.modules.academic.year.dto;

import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.academic.year.entity.AcademicYear}
 * surfaced via REST.
 *
 * <p>Used by detail endpoints ({@code GET /years/{publicUuid}}) and as the
 * return type of {@code POST}, {@code PUT} and {@code POST /activate}.</p>
 */
public record AcademicYearResponse(
		UUID publicUuid,
		String name,
		AcademicYearStatus status,
		LocalDate startDate,
		LocalDate endDate,
		Instant createdAt,
		Instant updatedAt
) {
}
