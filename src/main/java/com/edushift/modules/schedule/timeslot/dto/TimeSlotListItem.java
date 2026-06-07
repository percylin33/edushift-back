package com.edushift.modules.schedule.timeslot.dto;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Lean projection of a {@code TimeSlot} for the
 * {@code GET /teacher-assignments/{uuid}/time-slots} listing.
 *
 * <p>Drops the assignment ref because the caller already navigated
 * through the assignment URL — no need to repeat it on every row.</p>
 */
public record TimeSlotListItem(
		UUID publicUuid,
		Short dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		String classroom
) {
}
