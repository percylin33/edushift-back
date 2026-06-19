package com.edushift.modules.tasks.submission.dto;

import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Lean projection for the teacher list endpoint
 * ({@code GET /tasks/{uuid}/submissions}).
 */
public record SubmissionSummary(
		UUID publicUuid,
		UUID studentPublicUuid,
		SubmissionStatus status,
		Integer grade,
		boolean hasAttachment,
		Instant createdAt
) {
}
