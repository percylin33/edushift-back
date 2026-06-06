package com.edushift.modules.academic.period.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicPeriodResponse(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		PeriodType periodType,
		Integer ordinal,
		String name,
		LocalDate startDate,
		LocalDate endDate,
		Instant createdAt,
		Instant updatedAt
) {
}
