package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full representation of an
 * {@link com.edushift.modules.attendance.entity.AttendanceSession}
 * (Sprint 6 / BE-6.2.D).
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code POST /attendance/sessions} (open).</li>
 *   <li>{@code POST /attendance/sessions/{id}/close} (close).</li>
 *   <li>{@code GET /attendance/sessions} list (each item).</li>
 *   <li>{@code GET /attendance/sessions/{id}} (detail).</li>
 * </ul>
 *
 * <p>The four counters
 * ({@code presentCount}/{@code lateCount}/{@code absentCount}/
 * {@code excusedCount}) are populated when the response represents a
 * CLOSED session. For ACTIVE sessions they reflect the snapshot at
 * read time (alumnos materialized as {@code ABSENT} only after close).
 *
 * @param wasIdempotent {@code true} when {@code POST /sessions}
 *                      returned an existing ACTIVE row instead of
 *                      creating a new one. {@code null} on read paths.
 */
public record AttendanceSessionResponse(
		UUID publicUuid,
		UUID sectionPublicUuid,
		LocalDate occurredOn,
		AttendanceSessionSlot slot,
		AttendanceSessionStatus status,
		Instant startsAt,
		Instant closedAt,
		UserRef closedBy,
		String notes,
		Long presentCount,
		Long lateCount,
		Long absentCount,
		Long excusedCount,
		Boolean wasIdempotent,
		Instant createdAt,
		Instant updatedAt
) {
}
