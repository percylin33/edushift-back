package com.edushift.modules.tasks.dto;

import com.edushift.modules.tasks.entity.TaskStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Lean projection for list endpoints
 * ({@code GET /sections/{uuid}/tasks}).
 *
 * <p>{@code status} was added as part of BUG-2026-07-31-04 so the FE
 * can decide whether to show publish/archive actions.
 * Assignment fields added in V105 for course-scoped LMS.
 */
public record TaskSummary(
		UUID publicUuid,
		String title,
		Instant dueAt,
		boolean hasAttachment,
		UUID ownerPublicUuid,
		int maxScore,
		TaskStatus status,
		Instant createdAt,
		UUID teacherAssignmentPublicUuid,
		UUID coursePublicUuid,
		String courseName,
		String periodLabel,
		int submissionsCount,
		int gradedSubmissionsCount
) {
}
