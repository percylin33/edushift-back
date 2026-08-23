package com.edushift.modules.schedule.daytemplate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/**
 * Payload of {@code POST /schedule/day-templates/{uuid}/apply-day-plan}.
 * Regenerates RECESS/LUNCH blocks and stores day window metadata (ADR-SCH-12).
 */
public record ApplyDayPlanRequest(
		@NotNull LocalTime dayStart,
		@NotNull LocalTime dayEnd,
		@NotNull @Min(15) @Max(120) Integer periodMinutes,
		@Valid List<DayPlanWindowRequest> recesses,
		@Valid DayPlanWindowRequest lunch
) {
	public record DayPlanWindowRequest(
			@NotNull LocalTime startTime,
			@NotNull LocalTime endTime,
			@Size(max = 80) String label
	) {
	}
}
