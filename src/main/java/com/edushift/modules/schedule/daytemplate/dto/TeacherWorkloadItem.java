package com.edushift.modules.schedule.daytemplate.dto;

import java.util.UUID;

public record TeacherWorkloadItem(
		UUID teacherPublicUuid,
		String firstName,
		String lastName,
		int minutesPerWeek,
		int slotCount,
		int sectionCount,
		int courseCount
) {
}
