package com.edushift.modules.family.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Parent-safe grade row for one linked child.
 */
public record FamilyGradeItemDto(
		UUID publicUuid,
		UUID evaluationPublicUuid,
		String evaluationName,
		String sectionName,
		String courseName,
		BigDecimal score,
		BigDecimal maxScore,
		String literal,
		String comments,
		Instant scheduledDate,
		Instant recordedAt
) {
}
