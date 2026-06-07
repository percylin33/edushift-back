package com.edushift.modules.schedule.timeslot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Payload of {@code POST /v1/teacher-assignments/{assignmentUuid}/time-slots}.
 *
 * <p>Validation rules cross-checked at the service layer:</p>
 * <ul>
 *   <li>{@code endTime > startTime} → 400 {@code TIME_SLOT_DATE_INVERTED}.</li>
 *   <li>No overlap with another slot of the same assignment+day →
 *       409 {@code TIME_SLOT_OVERLAP}.</li>
 *   <li>Assignment must be active (not soft-ended) →
 *       409 {@code ASSIGNMENT_NOT_ACTIVE}.</li>
 * </ul>
 */
public record CreateTimeSlotRequest(

		@NotNull(message = "dayOfWeek is required")
		@Min(value = 1, message = "dayOfWeek must be 1..7 (ISO-8601)")
		@Max(value = 7, message = "dayOfWeek must be 1..7 (ISO-8601)")
		Short dayOfWeek,

		@NotNull(message = "startTime is required")
		LocalTime startTime,

		@NotNull(message = "endTime is required")
		LocalTime endTime,

		@Size(max = 80, message = "classroom too long")
		String classroom
) {
}
