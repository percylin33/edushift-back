package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/attendance/manual-check-in}
 * (Sprint 6 / BE-6.8 — manual fallback).
 *
 * <p>Used by the "auxiliar en la entrada" flow: the operator picks a
 * student by name + level/grade/section filters when the student's QR
 * card is missing. The backend auto-resolves which session the scan
 * lands on:
 *
 * <ol>
 *   <li>Find the student's current ACTIVE enrollment ⇒ section.</li>
 *   <li>Find or open the ACTIVE {@code AttendanceSession} for
 *       ({@code section}, {@link #effectiveOccurredOn()},
 *       {@link #effectiveSlot(Instant)}).</li>
 *   <li>Register the check-in idempotently on that session.</li>
 * </ol>
 *
 * @param studentPublicUuid the alumno to mark present. Required.
 * @param slot              optional override for the slot (defaults to
 *                          a wall-clock heuristic — see
 *                          {@link #effectiveSlot(Instant)}).
 * @param occurredOn        optional override for the calendar day
 *                          (defaults to {@code LocalDate.now()}).
 * @param occurredAt        optional override for the precise instant
 *                          of the scan (defaults to {@code Instant.now()};
 *                          forwarded to the existing PRESENT/LATE
 *                          calculation).
 * @param forcedStatus      TENANT_ADMIN-only override to bypass
 *                          PRESENT/LATE computation. Same semantics as
 *                          {@link CheckInRequest#forcedStatus()}.
 */
public record ManualCheckInRequest(
		@NotNull UUID studentPublicUuid,
		AttendanceSessionSlot slot,
		LocalDate occurredOn,
		Instant occurredAt,
		AttendanceRecordStatus forcedStatus
) {

	/** Returns {@link #occurredOn} or {@code LocalDate.now()}. */
	public LocalDate effectiveOccurredOn() {
		return occurredOn == null ? LocalDate.now() : occurredOn;
	}

	/**
	 * Returns the slot to bind the resolved session to. Order of precedence:
	 * <ol>
	 *   <li>Explicit {@link #slot} on the request.</li>
	 *   <li>Wall-clock heuristic: {@code MORNING} before 12:00,
	 *       {@code AFTERNOON} otherwise. {@code FULL_DAY} stays admin-driven.</li>
	 * </ol>
	 *
	 * @param reference the reference instant to derive the slot from
	 *                  (typically {@code Instant.now()} at the service
	 *                  layer, exposed as a parameter for testability).
	 */
	public AttendanceSessionSlot effectiveSlot(Instant reference) {
		if (slot != null) return slot;
		Instant ref = reference == null ? Instant.now() : reference;
		int hour = ref.atZone(java.time.ZoneId.systemDefault()).getHour();
		return hour < 12 ? AttendanceSessionSlot.MORNING : AttendanceSessionSlot.AFTERNOON;
	}
}
