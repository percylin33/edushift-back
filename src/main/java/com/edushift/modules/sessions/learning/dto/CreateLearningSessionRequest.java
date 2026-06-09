package com.edushift.modules.sessions.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/learning-sessions}.
 *
 * <p>Cross-context validation is delegated to the service layer:</p>
 * <ul>
 *   <li>{@code scheduledDate} must lie within the assignment's period
 *       window - 400 {@code SESSION_DATE_OUT_OF_PERIOD}.</li>
 *   <li>{@code unitUuid} must belong to the assignment's course - 400
 *       {@code UNIT_NOT_IN_COURSE}.</li>
 *   <li>Each {@code competencyUuids[]} entry must belong to the
 *       assignment's course - 400 {@code COMPETENCY_NOT_IN_COURSE}.</li>
 *   <li>Each {@code capacityUuids[]} entry must belong to a competency
 *       of the assignment's course - 400 {@code CAPACITY_NOT_IN_COURSE}.</li>
 *   <li>The assignment must be active - 409 {@code ASSIGNMENT_NOT_ACTIVE}.</li>
 * </ul>
 */
public record CreateLearningSessionRequest(

		@NotNull(message = "assignmentUuid is required")
		UUID assignmentUuid,

		@NotNull(message = "unitUuid is required")
		UUID unitUuid,

		@NotBlank(message = "title is required")
		@Size(max = 200, message = "title too long")
		String title,

		@Size(max = 1000, message = "objective too long")
		String objective,

		@NotNull(message = "scheduledDate is required")
		LocalDate scheduledDate,

		@NotNull(message = "durationMinutes is required")
		@Min(value = 1, message = "durationMinutes must be >= 1")
		@Max(value = 480, message = "durationMinutes must be <= 480 (8 hours)")
		Integer durationMinutes,

		@Valid
		SessionContentDto content,

		List<UUID> competencyUuids,

		List<UUID> capacityUuids
) {
}
