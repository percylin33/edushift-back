package com.edushift.modules.tasks.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * PATCH body for a task. All fields are optional but at least one
 * must be present (the service raises {@code RECORD_EMPTY_PATCH}
 * when none are). {@code dueAt} is validated {@code @Future} at
 * the DTO level; the service re-checks because Bean Validation
 * does not apply to null fields.
 */
public record UpdateTaskRequest(
		@Size(max = 200) String title,
		@Size(max = 10000) String description,
		@Future Instant dueAt,
		UUID attachmentPublicUuid,
		Boolean allowResubmission
) {
}
