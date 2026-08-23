package com.edushift.modules.schedule.daytemplate.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CloneScheduleRequest(
		@NotNull UUID sourceYearUuid,
		boolean includeTimeSlots
) {
}
