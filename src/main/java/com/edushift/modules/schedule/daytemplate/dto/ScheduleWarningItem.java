package com.edushift.modules.schedule.daytemplate.dto;

import java.util.UUID;

public record ScheduleWarningItem(
		String code,
		String severity,
		String message,
		UUID sectionUuid,
		UUID gradeUuid,
		UUID teacherUuid
) {
	public static final String SEVERITY_WARN = "WARN";

	public static ScheduleWarningItem warn(String code, String message,
			UUID sectionUuid, UUID gradeUuid, UUID teacherUuid) {
		return new ScheduleWarningItem(code, SEVERITY_WARN, message,
				sectionUuid, gradeUuid, teacherUuid);
	}
}
