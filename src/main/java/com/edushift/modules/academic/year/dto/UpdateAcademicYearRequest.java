package com.edushift.modules.academic.year.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload of {@code PUT /v1/academic/years/{publicUuid}}.
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li>{@code null} = no change.</li>
 *   <li>Status is immutable through this endpoint: lifecycle changes go
 *       through {@code POST /years/{publicUuid}/activate} and (future)
 *       {@code POST /years/{publicUuid}/close}.</li>
 *   <li>If the year is {@link com.edushift.modules.academic.year.entity.AcademicYearStatus#CLOSED},
 *       any non-empty patch is rejected with {@code ACADEMIC_YEAR_LOCKED}.</li>
 * </ul>
 */
public record UpdateAcademicYearRequest(

		@Size(min = 1, max = 50, message = "name length out of range")
		String name,

		LocalDate startDate,

		LocalDate endDate
) {

	public boolean isEmpty() {
		return name == null && startDate == null && endDate == null;
	}
}
