package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.schedule.daytemplate.dto.SuggestedPeriodItem;
import com.edushift.shared.exception.BadRequestException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure helper: validate day-plan windows and carve academic periods between
 * hard non-teaching intervals (ADR-SCH-12).
 */
public final class DayPlanCalculator {

	private DayPlanCalculator() {
	}

	public record Interval(LocalTime start, LocalTime end) {
		public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
			return start.isBefore(otherEnd) && otherStart.isBefore(end);
		}
	}

	public static void validateDayWindow(LocalTime dayStart, LocalTime dayEnd, int periodMinutes) {
		if (dayStart == null || dayEnd == null) {
			throw new BadRequestException("DAY_PLAN_WINDOW_REQUIRED",
					"dayStart and dayEnd are required");
		}
		if (!dayEnd.isAfter(dayStart)) {
			throw new BadRequestException("DAY_PLAN_WINDOW_INVERTED",
					"dayEnd must be strictly after dayStart");
		}
		if (periodMinutes < 15 || periodMinutes > 120) {
			throw new BadRequestException("DAY_PLAN_PERIOD_INVALID",
					"periodMinutes must be between 15 and 120");
		}
	}

	public static void validateWindowInsideDay(LocalTime dayStart, LocalTime dayEnd,
			LocalTime start, LocalTime end, String code) {
		if (start == null || end == null) {
			throw new BadRequestException(code, "startTime and endTime are required");
		}
		if (!end.isAfter(start)) {
			throw new BadRequestException(code, "endTime must be strictly after startTime");
		}
		if (start.isBefore(dayStart) || end.isAfter(dayEnd)) {
			throw new BadRequestException(code,
					"Window must lie entirely within dayStart..dayEnd");
		}
	}

	/**
	 * Walks {@code [dayStart, dayEnd)} in {@code periodMinutes} slices, skipping
	 * any overlap with hard blocks (recess/lunch/assembly).
	 */
	public static List<SuggestedPeriodItem> computeSuggestedPeriods(
			LocalTime dayStart,
			LocalTime dayEnd,
			int periodMinutes,
			List<Interval> hardBlocks
	) {
		if (dayStart == null || dayEnd == null || periodMinutes < 15) {
			return List.of();
		}
		List<Interval> sorted = hardBlocks == null ? List.of() : hardBlocks.stream()
				.sorted(Comparator.comparing(Interval::start))
				.toList();

		List<SuggestedPeriodItem> periods = new ArrayList<>();
		LocalTime cursor = dayStart;
		int ordinal = 1;

		while (cursor.plusMinutes(periodMinutes).compareTo(dayEnd) <= 0) {
			LocalTime sliceEnd = cursor.plusMinutes(periodMinutes);
			Interval blocking = firstOverlap(sorted, cursor, sliceEnd);
			if (blocking != null) {
				cursor = blocking.end();
				if (cursor.isBefore(dayStart) || !cursor.isBefore(dayEnd)) {
					break;
				}
				continue;
			}
			periods.add(new SuggestedPeriodItem(
					ordinal,
					cursor,
					sliceEnd,
					"Periodo " + ordinal
			));
			ordinal++;
			cursor = sliceEnd;
		}
		return periods;
	}

	private static Interval firstOverlap(List<Interval> hardBlocks,
			LocalTime start, LocalTime end) {
		for (Interval block : hardBlocks) {
			if (block.overlaps(start, end)) {
				return block;
			}
		}
		return null;
	}
}
