package com.edushift.modules.family.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.JustificationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Parent-safe attendance row for one linked child.
 */
public record FamilyAttendanceRecordDto(
		UUID publicUuid,
		UUID sessionPublicUuid,
		UUID studentPublicUuid,
		String studentFullName,
		AttendanceRecordStatus status,
		Instant occurredAt,
		String notes,
		JustificationStatus justificationStatus,
		String justificationText,
		Instant approvedAt
) {
}
