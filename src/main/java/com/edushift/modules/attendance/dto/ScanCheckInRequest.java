package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Body of {@code POST /api/v1/attendance/scan-check-in} — the
 * "session-less" QR scan flow (BE-6.8.b).
 *
 * <p>Mirrors {@link CheckInRequest} but drops {@code sessionPublicUuid}:
 * the backend auto-resolves the target {@link com.edushift.modules.attendance.entity.AttendanceSession}
 * from the {@code Student}'s current ACTIVE enrollment, just like the
 * manual check-in fallback. Designed for the "auxiliar en la entrada
 * del colegio" use case where the scanner attends N sections at once
 * and pre-opening a session for each is impractical.</p>
 *
 * @param qrToken      the raw JWT scanned from the student's QR card.
 *                     Validated with HS256 against {@code JWT_SECRET}.
 * @param occurredAt   override the scan timestamp (defaults to
 *                     {@code Instant.now()}); same drift guard as the
 *                     classic check-in.
 * @param forcedStatus admin-only override that bypasses the
 *                     PRESENT/LATE computation.
 */
public record ScanCheckInRequest(
		@NotBlank String qrToken,
		Instant occurredAt,
		AttendanceRecordStatus forcedStatus
) {
}
