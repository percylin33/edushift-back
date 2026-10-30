package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
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
		@Valid DayPlanWindowRequest lunch,
		@Valid @Size(max = 40) List<DayPlanActivityRequest> activities
) {
	public record DayPlanWindowRequest(
			@NotNull LocalTime startTime,
			@NotNull LocalTime endTime,
			@Size(max = 80) String label
	) {
	}

	public record DayPlanActivityRequest(
			@NotNull DayBlockType blockType,
			@Min(1) @Max(5) Short dayOfWeek,
			@NotNull LocalTime startTime,
			@NotNull LocalTime endTime,
			@Size(max = 80) String label
	) {
	}
}
