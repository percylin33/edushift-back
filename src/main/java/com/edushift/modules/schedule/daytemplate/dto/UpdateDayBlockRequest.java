package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateDayBlockRequest(
		@Min(1) @Max(7) Short dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		DayBlockType blockType,
		@Size(max = 80) String label
) {
}
