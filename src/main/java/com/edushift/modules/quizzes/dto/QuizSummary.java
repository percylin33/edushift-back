package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.QuizStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Slim projection of a quiz for list endpoints (Sprint 7b / BE-7b.1 + V105).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizSummary(
		UUID publicUuid,
		String title,
		QuizStatus status,
		Instant dueAt,
		Integer timeLimitMinutes,
		Integer maxAttempts,
		Integer maxScore,
		UUID ownerPublicUuid,
		int questionCount,
		int totalPoints,
		Instant createdAt,
		UUID teacherAssignmentPublicUuid,
		UUID coursePublicUuid,
		String courseName,
		String periodLabel
) {
}
