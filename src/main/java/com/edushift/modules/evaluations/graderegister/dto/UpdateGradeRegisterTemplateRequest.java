package com.edushift.modules.evaluations.graderegister.dto;

import com.edushift.modules.evaluations.entity.EvaluationScale;
import jakarta.validation.constraints.Size;

public record UpdateGradeRegisterTemplateRequest(

		@Size(max = 120)
		String name,

		@Size(max = 500)
		String description,

		Boolean isDefault,

		/** SCORE_0_20 or LITERAL_CNEB. Null leaves the current scale. */
		EvaluationScale gradingScale
) {
}
