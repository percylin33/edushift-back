package com.edushift.modules.evaluations.graderegister.dto;

import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateGradeCategoryRequest(

		@NotBlank
		@Size(max = 80)
		String name,

		@Size(max = 32)
		String colorToken,

		@NotNull
		Integer sortOrder,

		@NotNull
		@DecimalMin("0.00")
		@DecimalMax("999.99")
		BigDecimal weight,

		Boolean contributesToAverage,

		Boolean allowsTeacherColumns,

		GradeCategoryColumnMode columnMode,

		@Min(0)
		@Max(40)
		Integer slotCount,

		@Size(max = 16)
		String slotLabelPrefix,

		List<@Size(max = 40) String> slotLabels
) {
}
