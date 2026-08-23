package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import java.time.LocalTime;
import java.util.UUID;

public record DayBlockResponse(
		UUID publicUuid,
		Short dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		DayBlockType blockType,
		String label
) {
}
