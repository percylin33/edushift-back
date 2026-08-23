package com.edushift.modules.schedule.timeslot.dto;

import com.edushift.modules.schedule.daytemplate.dto.NonTeachingBlockItem;
import com.edushift.modules.schedule.daytemplate.dto.SuggestedPeriodItem;
import java.time.LocalTime;
import java.util.List;

/**
 * Unified weekly schedule payload: teaching {@link ScheduleSlotItem}s plus
 * non-teaching blocks (recess, lunch, assembly) from the day template, and
 * optional suggested academic periods (ADR-SCH-12).
 */
public record ScheduleWeekView(
		List<ScheduleSlotItem> slots,
		List<NonTeachingBlockItem> nonTeachingBlocks,
		LocalTime dayStart,
		LocalTime dayEnd,
		Integer periodMinutes,
		List<SuggestedPeriodItem> suggestedPeriods
) {
	public static ScheduleWeekView of(List<ScheduleSlotItem> slots,
			List<NonTeachingBlockItem> nonTeachingBlocks) {
		return of(slots, nonTeachingBlocks, null, null, null, List.of());
	}

	public static ScheduleWeekView of(List<ScheduleSlotItem> slots,
			List<NonTeachingBlockItem> nonTeachingBlocks,
			LocalTime dayStart,
			LocalTime dayEnd,
			Integer periodMinutes,
			List<SuggestedPeriodItem> suggestedPeriods) {
		return new ScheduleWeekView(
				slots != null ? slots : List.of(),
				nonTeachingBlocks != null ? nonTeachingBlocks : List.of(),
				dayStart,
				dayEnd,
				periodMinutes,
				suggestedPeriods != null ? suggestedPeriods : List.of());
	}
}
