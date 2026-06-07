package com.edushift.modules.schedule.timeslot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Payload of {@code PUT /v1/time-slots/{publicUuid}}.
 *
 * <p>Partial-merge: {@code null} fields are ignored. Cross-field rules
 * ({@code endTime > startTime}, no overlap) re-evaluate against the
 * post-merge state, not just the patch.</p>
 *
 * <p>{@code teacher_assignment_id} is intentionally NOT exposed —
 * moving a slot between assignments would force a service-level
 * cascade that's safer modeled as "delete + recreate".</p>
 */
public record UpdateTimeSlotRequest(

		@Min(value = 1, message = "dayOfWeek must be 1..7 (ISO-8601)")
		@Max(value = 7, message = "dayOfWeek must be 1..7 (ISO-8601)")
		Short dayOfWeek,

		LocalTime startTime,

		LocalTime endTime,

		@Size(max = 80, message = "classroom too long")
		String classroom
) {

	public boolean isEmpty() {
		return dayOfWeek == null
				&& startTime == null
				&& endTime == null
				&& classroom == null;
	}
}
