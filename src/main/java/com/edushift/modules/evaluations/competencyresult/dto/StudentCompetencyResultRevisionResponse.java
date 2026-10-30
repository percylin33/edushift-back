package com.edushift.modules.evaluations.competencyresult.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StudentCompetencyResultRevisionResponse(
		Integer revision,
		Map<String, Object> snapshot,
		UUID changedByUserId,
		Instant changedAt) {
}
