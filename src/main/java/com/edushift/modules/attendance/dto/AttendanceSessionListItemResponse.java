package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Slim representation of an {@code AttendanceSession} used by the
 * listing endpoint {@code GET /v1/attendance/sessions} (Sprint 6 /
 * BE-6.7).
 *
 * <h3>Why a dedicated DTO</h3>
 * The full {@link AttendanceSessionResponse} carries the four
 * counters ({@code presentCount} / {@code lateCount} / …) which
 * require an extra {@code count} per row — too expensive on a list.
 * A listing only needs what the FE table renders: public UUIDs, the
 * section label, the date / slot / status, and timestamps for sort
 * and the "recent" widget. The full DTO is still returned by the
 * single-session endpoints.
 *
 * <h3>Stable field order</h3>
 * Records preserve the declaration order, so adding a field
 * <em>at the end</em> is non-breaking for clients; inserting a
 * field anywhere is not. Keep the new field at the tail and bump
 * the API version if a breaking re-order is ever needed.
 */
public record AttendanceSessionListItemResponse(
		UUID publicUuid,
		UUID sectionPublicUuid,
		String sectionName,
		String sectionGradeName,
		LocalDate occurredOn,
		AttendanceSessionSlot slot,
		AttendanceSessionStatus status,
		Instant startsAt,
		Instant closedAt,
		Long presentCount,
		Long lateCount,
		Long absentCount,
		Long excusedCount,
		Instant createdAt,
		Instant updatedAt
) {
}
