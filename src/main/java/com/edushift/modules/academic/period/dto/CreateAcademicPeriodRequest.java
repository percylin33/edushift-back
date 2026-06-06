package com.edushift.modules.academic.period.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/academic/periods}.
 *
 * <p>If {@code name} is null/blank the service auto-generates it as
 * {@code "<roman_ordinal> <PeriodType.displayLabel>"} — e.g. ordinal=2,
 * type=BIMESTRE → {@code "II Bimestre"}.</p>
 */
public record CreateAcademicPeriodRequest(

		@NotNull(message = "academicYearPublicUuid is required")
		UUID academicYearPublicUuid,

		@NotNull(message = "periodType is required")
		PeriodType periodType,

		@NotNull(message = "ordinal is required")
		@Min(value = 1, message = "ordinal must be >= 1")
		Integer ordinal,

		@Size(max = 60, message = "name too long")
		String name,

		@NotNull(message = "startDate is required")
		LocalDate startDate,

		@NotNull(message = "endDate is required")
		LocalDate endDate
) {
}
