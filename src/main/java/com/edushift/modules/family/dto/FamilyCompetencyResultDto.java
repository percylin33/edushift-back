package com.edushift.modules.family.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Parent-safe view of a competency result from a closed evaluation. */
public record FamilyCompetencyResultDto(
		UUID publicUuid,
		String evaluationName,
		LocalDate evaluationDate,
		String courseName,
		String sectionName,
		String competencyCode,
		String competencyName,
		String achievementLevel,
		String evidenceSummary,
		String descriptiveConclusion,
		Instant recordedAt) {
}
