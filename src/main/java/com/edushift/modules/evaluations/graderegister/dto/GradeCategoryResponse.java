package com.edushift.modules.evaluations.graderegister.dto;

import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GradeCategoryResponse(
		UUID publicUuid,
		String name,
		String colorToken,
		Integer sortOrder,
		BigDecimal weight,
		boolean contributesToAverage,
		boolean allowsTeacherColumns,
		GradeCategoryColumnMode columnMode,
		Integer slotCount,
		String slotLabelPrefix,
		List<String> slotLabels
) {
}
