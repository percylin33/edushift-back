package com.edushift.modules.schedule.daytemplate.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record DayTemplateResponse(
		UUID publicUuid,
		UUID yearUuid,
		UUID levelUuid,
		String levelCode,
		UUID gradeUuid,
		String gradeName,
		String shift,
		String name,
		String recessShareGroup,
		LocalTime dayStart,
		LocalTime dayEnd,
		Integer periodMinutes,
		List<DayBlockResponse> blocks
) {
}
