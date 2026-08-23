package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import java.time.LocalTime;
import java.util.UUID;

/** Flat non-teaching block for reverse schedule views. */
public record NonTeachingBlockItem(
		UUID blockPublicUuid,
		Short dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		DayBlockType blockType,
		String label,
		String levelCode,
		String gradeName
) {
}
