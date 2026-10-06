package com.edushift.modules.attendance.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published after an attendance session is closed and ABSENT rows were
 * materialized. Listeners run {@code AFTER_COMMIT} so guardian fan-out
 * and {@code STUDENT_ABSENT} notifications do not extend the close
 * transaction (morning/evening close spikes).
 */
public record SessionClosedAbsencesEvent(
		UUID tenantId,
		UUID sessionPublicUuid,
		String sectionName,
		Instant absentAt,
		List<AbsentStudent> absents
) {
	public record AbsentStudent(
			UUID studentId,
			UUID studentPublicUuid,
			String studentFullName,
			UUID recordPublicUuid
	) {
	}
}
