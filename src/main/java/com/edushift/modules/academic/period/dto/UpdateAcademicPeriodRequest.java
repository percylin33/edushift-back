package com.edushift.modules.academic.period.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload of {@code PUT /v1/academic/periods/{publicUuid}}.
 *
 * <p>Partial-merge. Reordering the period (changing
 * {@code ordinal}/{@code periodType}) is intentionally NOT allowed —
 * delete &amp; recreate is safer because of the contiguous-ordinal
 * invariant. The {@code year} is also immutable for the same reason.</p>
 */
public record UpdateAcademicPeriodRequest(

		@Size(max = 60, message = "name too long")
		String name,

		LocalDate startDate,

		LocalDate endDate
) {

	public boolean isEmpty() {
		return name == null && startDate == null && endDate == null;
	}
}
