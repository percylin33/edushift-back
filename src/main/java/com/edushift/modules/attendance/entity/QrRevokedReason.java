package com.edushift.modules.attendance.entity;

/**
 * Reason a {@link StudentAttendanceQr} row was revoked (Sprint 6 / BE-6.1).
 *
 * <ul>
 *   <li>{@code ROTATED} - admin rotation via
 *       {@code POST /students/{uuid}/attendance-qr/rotate}. Default
 *       reason; the new active QR replaces the previous one in the
 *       same transaction.
 *   <li>{@code LOST} - student reported their printed credential lost.
 *       Operationally identical to {@code ROTATED}, kept distinct for
 *       reporting (we want to know how many credentials get lost
 *       to rotate the printed-card supply).
 *   <li>{@code ADMIN_REVOKE} - security incident or off-boarding;
 *       the QR is revoked WITHOUT issuing a replacement. Re-issue
 *       requires a new explicit POST after the incident is cleared.
 * </ul>
 */
public enum QrRevokedReason {

	ROTATED,
	LOST,
	ADMIN_REVOKE
}
