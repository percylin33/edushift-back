package com.edushift.modules.schedule.daytemplate.dto;

import java.time.LocalTime;

/**
 * Suggested academic period (empty cell) derived from day plan metadata.
 * Not persisted — used by week views so the FE can bind a TimeSlot on click.
 */
public record SuggestedPeriodItem(
		int ordinal,
		LocalTime startTime,
		LocalTime endTime,
		String label
) {
}
