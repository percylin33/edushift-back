package com.edushift.modules.evaluations.competencyresult.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentCompetencyResultResponse(
		UUID publicUuid,
		UUID competencyPublicUuid,
		String competencyCode,
		String competencyName,
		String achievementLevel,
		String evidenceSummary,
		String descriptiveConclusion,
		List<CriterionResultInput> criteriaResults,
		Integer revision,
		Instant recordedAt,
		UUID recordedByUserId) {
}
