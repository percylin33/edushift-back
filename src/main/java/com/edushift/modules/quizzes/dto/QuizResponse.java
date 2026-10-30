package com.edushift.modules.quizzes.dto;

import com.edushift.modules.quizzes.entity.QuizStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of a quiz with nested questions (Sprint 7b / BE-7b.1 + V105).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizResponse(
		UUID publicUuid,
		UUID sectionPublicUuid,
		String title,
		String description,
		QuizStatus status,
		Instant dueAt,
		Integer timeLimitMinutes,
		Integer maxAttempts,
		Integer maxScore,
		UUID ownerPublicUuid,
		Instant publishedAt,
		Instant closedAt,
		UUID rubricPublicUuid,
		UUID rubricEvaluationPublicUuid,
		int questionCount,
		int totalPoints,
		boolean revealCorrectness,
		List<QuestionResponse> questions,
		Instant createdAt,
		Instant updatedAt,
		UUID teacherAssignmentPublicUuid,
		UUID coursePublicUuid,
		String courseName,
		String periodLabel
) {
}
