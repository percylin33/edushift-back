package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record CreateDayBlockRequest(
		@Min(1) @Max(7) Short dayOfWeek,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime,
		@NotNull DayBlockType blockType,
		@NotBlank @Size(max = 80) String label
) {
}
