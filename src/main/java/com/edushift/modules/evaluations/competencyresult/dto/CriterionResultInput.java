package com.edushift.modules.evaluations.competencyresult.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriterionResultInput(
		@NotBlank @Size(max = 180) String criterionName,
		@NotBlank @Size(max = 32) String achievementLevel,
		@Size(max = 2000) String evidence,
		@Size(max = 2000) String feedback) {
}
