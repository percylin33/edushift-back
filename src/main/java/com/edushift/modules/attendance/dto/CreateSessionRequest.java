package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/attendance/sessions}
 * (Sprint 6 / BE-6.2.D).
 *
 * <p>The service treats {@code (sectionPublicUuid, occurredOn, slot)}
 * as the idempotency key: opening twice with the same triple while a
 * session is still ACTIVE returns the existing row without creating a
 * duplicate (mirrors {@code uk_attendance_sessions_section_day_slot_active}).
 *
 * @param sectionPublicUuid public UUID of the {@code Section} the
 *                          session is bound to.
 * @param occurredOn        local calendar date of the session.
 * @param slot              time-of-day slot (defaults to
 *                          {@code FULL_DAY} when omitted).
 * @param startsAt          reference timestamp used to compute LATE.
 *                          When omitted the service uses
 *                          {@code Instant.now()}.
 * @param notes             optional free-form note (≤ 500 chars).
 */
public record CreateSessionRequest(
		@NotNull UUID sectionPublicUuid,
		@NotNull LocalDate occurredOn,
		AttendanceSessionSlot slot,
		Instant startsAt,
		@Size(max = 500) String notes
) {

	/** Returns {@link #slot} or the {@code FULL_DAY} default. */
	public AttendanceSessionSlot effectiveSlot() {
		return slot == null ? AttendanceSessionSlot.FULL_DAY : slot;
	}
}
