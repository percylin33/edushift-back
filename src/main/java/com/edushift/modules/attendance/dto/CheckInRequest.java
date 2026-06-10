package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/attendance/check-in}
 * (Sprint 6 / BE-6.2.D).
 *
 * @param qrToken      the raw JWT scanned from the student's QR card.
 *                     Validated with HS256 against {@code JWT_SECRET}.
 * @param sessionPublicUuid the {@code AttendanceSession} the scan is
 *                     happening in. Must be {@code ACTIVE}.
 * @param occurredAt   override the scan timestamp (defaults to
 *                     {@code Instant.now()}). Rejected if it drifts
 *                     more than {@code edushift.attendance.future-drift-tolerance-minutes}
 *                     into the future.
 * @param forcedStatus admin-only override that bypasses the
 *                     PRESENT/LATE computation. Sent by a non-admin
 *                     yields 403 {@code FORCED_STATUS_FORBIDDEN}.
 */
public record CheckInRequest(
		@NotBlank String qrToken,
		@NotNull UUID sessionPublicUuid,
		Instant occurredAt,
		AttendanceRecordStatus forcedStatus
) {
}
