package com.edushift.modules.academic.period.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicPeriodListItem(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		PeriodType periodType,
		Integer ordinal,
		String name,
		LocalDate startDate,
		LocalDate endDate
) {
}
