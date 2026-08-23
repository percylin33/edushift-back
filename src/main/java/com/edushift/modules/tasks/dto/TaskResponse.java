package com.edushift.modules.tasks.dto;

import com.edushift.modules.tasks.entity.TaskStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Full projection of a {@link com.edushift.modules.tasks.entity.Task}.
 * {@code attachmentPublicUuid} is non-null when the task has a
 * teacher-uploaded handout.
 *
 * <p>{@code status}, {@code publishedAt} and {@code archivedAt} were
 * added as part of BUG-2026-07-31-04 (lifecycle endpoints).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(
		UUID publicUuid,
		UUID sectionPublicUuid,
		String title,
		String description,
		Instant dueAt,
		UUID attachmentPublicUuid,
		UUID ownerPublicUuid,
		boolean allowResubmission,
		TaskStatus status,
		Instant publishedAt,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt
) {
}
