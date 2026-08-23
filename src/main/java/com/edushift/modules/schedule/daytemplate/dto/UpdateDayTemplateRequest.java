package com.edushift.modules.schedule.daytemplate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateDayTemplateRequest(
		@Size(max = 20) String shift,
		@Size(max = 120) String name,
		@Size(max = 80) String recessShareGroup,
		LocalTime dayStart,
		LocalTime dayEnd,
		@Min(15) @Max(120) Integer periodMinutes
) {
}
