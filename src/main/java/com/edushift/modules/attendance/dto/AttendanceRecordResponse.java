package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Representation of a single attendance row, real or virtual
 * (Sprint 6 / BE-6.2.D).
 *
 * <h3>Virtual rows</h3>
 * The roster endpoint
 * ({@code GET /attendance/sessions/{id}/records}) returns one row per
 * enrolled student. Students without a real record show:
 * <ul>
 *   <li>{@code publicUuid=null}, {@code status=null} when the parent
 *       session is {@code ACTIVE} (still in-progress).</li>
 *   <li>{@code publicUuid=null}, {@code status=ABSENT} when the parent
 *       session is {@code CLOSED} but the materialization step did
 *       not insert (edge case — we expose the derived value).</li>
 * </ul>
 *
 * <p>Real rows always carry a {@code publicUuid} and a non-null
 * {@code status}.
 *
 * @param wasIdempotent {@code true} when {@code POST /check-in}
 *                      returned an existing record instead of creating
 *                      a new one. {@code null} on roster reads.
 */
public record AttendanceRecordResponse(
		UUID publicUuid,
		UUID sessionPublicUuid,
		UUID studentPublicUuid,
		String studentFullName,
		AttendanceRecordStatus status,
		Instant occurredAt,
		UserRef scannedBy,
		UserRef editedBy,
		Instant editedAt,
		String notes,
		Boolean wasIdempotent,
		Instant createdAt,
		Instant updatedAt
) {
}
