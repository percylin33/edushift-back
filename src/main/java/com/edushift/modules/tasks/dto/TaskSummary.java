package com.edushift.modules.tasks.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Lean projection for list endpoints
 * ({@code GET /sections/{uuid}/tasks}).
 */
public record TaskSummary(
		UUID publicUuid,
		String title,
		Instant dueAt,
		boolean hasAttachment,
		UUID ownerPublicUuid,
		Instant createdAt
) {
}
