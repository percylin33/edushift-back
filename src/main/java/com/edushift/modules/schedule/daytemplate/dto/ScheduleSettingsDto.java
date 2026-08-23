package com.edushift.modules.schedule.daytemplate.dto;

import com.edushift.modules.schedule.daytemplate.entity.RecessPolicy;
import java.util.List;

public record ScheduleSettingsDto(
		RecessPolicy recessPolicy,
		List<String> shareGroupLevelCodes
) {
}
