package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import java.time.Instant;
import java.util.UUID;

/** Lean row for staff student attendance history + exports. */
public record StudentAttendanceHistoryResponse(
        UUID publicUuid,
        Instant occurredAt,
        AttendanceRecordStatus status,
        String notes
) {}
