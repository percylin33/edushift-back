package com.edushift.modules.me.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.JustificationStatus;
import java.time.Instant;
import java.util.UUID;

public record MeAttendanceRecordResponse(
        UUID publicUuid,
        UUID sessionPublicUuid,
        AttendanceRecordStatus status,
        Instant occurredAt,
        String notes,
        JustificationStatus justificationStatus,
        String justificationText,
        Instant approvedAt
) {
}
