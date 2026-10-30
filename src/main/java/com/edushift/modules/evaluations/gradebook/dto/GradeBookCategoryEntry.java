package com.edushift.modules.evaluations.gradebook.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GradeBookCategoryEntry(
		UUID publicUuid,
		String name,
		String colorToken,
		Integer sortOrder,
		BigDecimal weight,
		boolean contributesToAverage,
		boolean allowsTeacherColumns
) {
}
