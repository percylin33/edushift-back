package com.edushift.modules.sessions.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code PUT /v1/learning-sessions/{publicUuid}}.
 *
 * <p>All fields are optional. {@code null} = "leave as is".
 * Lists with content (even empty) replace the previous association
 * set; lists set to {@code null} are no-ops.</p>
 *
 * <p>Updating is forbidden once the session is in a terminal state
 * (COMPLETED / CANCELLED). The service answers 409
 * {@code SESSION_TRANSITION_INVALID} in that case. Lifecycle state
 * itself cannot be changed via this endpoint - use
 * {@code /start}, {@code /complete}, or {@code /cancel} instead.</p>
 *
 * <p>{@code unitUuid} can be re-pointed to another unit of the same
 * course; cross-course moves return 400 {@code UNIT_NOT_IN_COURSE}.
 * The assignment itself is immutable - to move a session to another
 * assignment, delete and re-create.</p>
 */
public record UpdateLearningSessionRequest(

		UUID unitUuid,

		@Size(max = 200, message = "title too long")
		String title,

		@Size(max = 1000, message = "objective too long")
		String objective,

		LocalDate scheduledDate,

		@Min(value = 1, message = "durationMinutes must be >= 1")
		@Max(value = 480, message = "durationMinutes must be <= 480 (8 hours)")
		Integer durationMinutes,

		@Valid
		SessionContentDto content,

		List<UUID> competencyUuids,

		List<UUID> capacityUuids
) {

	/**
	 * @return true when every field is null (no merge happens).
	 */
	public boolean isEmpty() {
		return unitUuid == null
				&& title == null
				&& objective == null
				&& scheduledDate == null
				&& durationMinutes == null
				&& content == null
				&& competencyUuids == null
				&& capacityUuids == null;
	}
}
