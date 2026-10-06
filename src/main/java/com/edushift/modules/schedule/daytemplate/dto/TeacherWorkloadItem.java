package com.edushift.modules.schedule.daytemplate.dto;

import java.util.UUID;

/**
 * Weekly teaching load for one teacher in one academic period.
 *
 * <p>{@code academicHoursPerWeek} divides each slot by the section's
 * jornada {@code periodMinutes} (fallback 45), so a 90-minute block
 * counts as 2 academic hours when the hour is 45 minutes.</p>
 */
public record TeacherWorkloadItem(
		UUID teacherPublicUuid,
		String firstName,
		String lastName,
		int minutesPerWeek,
		double academicHoursPerWeek,
		int slotCount,
		int sectionCount,
		int courseCount
) {
}
