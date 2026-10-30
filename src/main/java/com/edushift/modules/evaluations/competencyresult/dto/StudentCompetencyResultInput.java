package com.edushift.modules.evaluations.competencyresult.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record StudentCompetencyResultInput(
		@NotNull UUID competencyPublicUuid,
		@NotBlank @Size(max = 32) String achievementLevel,
		@Size(max = 4000) String evidenceSummary,
		@Size(max = 4000) String descriptiveConclusion,
		@Size(max = 20) List<@Valid CriterionResultInput> criteriaResults) {
}
