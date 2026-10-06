package com.edushift.modules.sessions.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/learning-sessions/{publicUuid}/copy}.
 *
 * <p>The target assignment must belong to the caller and share the
 * source session's course, grade and period. The copy is a new
 * {@code PLANNED} session; observations are dropped.</p>
 */
public record CopyLearningSessionRequest(

		@NotNull(message = "assignmentUuid is required")
		UUID assignmentUuid,

		@NotNull(message = "scheduledDate is required")
		LocalDate scheduledDate
) {
}
