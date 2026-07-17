package com.edushift.modules.attendance.error;

/**
 * Stable error codes for the {@code attendance} module
 * (Sprint 6 / BE-6.2).
 *
 * <p>Codes are part of the public API contract — never rename. The full
 * error contract (HTTP status, recovery hints) is documented in
 * {@code docs/modules/attendance.md} §7.1.
 *
 * <h3>Grouping</h3>
 * <ul>
 *   <li>{@code SESSION_CLOSED} — write against a CLOSED session (409).</li>
 *   <li>{@code SESSION_ALREADY_OPEN} — open a duplicate ACTIVE session
 *       for {@code (section, occurredOn, slot)} (409).</li>
 *   <li>{@code QR_INVALID} — JWT signature, format, type or claims are
 *       wrong (401).</li>
 *   <li>{@code QR_EXPIRED} — the QR was revoked
 *       ({@code revoked_at IS NOT NULL}) (410).</li>
 *   <li>{@code QR_TENANT_MISMATCH} — the QR belongs to another tenant.
 *       Surfaced to the client as 404 {@code RESOURCE_NOT_FOUND}
 *       (anti-enumeration); kept as a distinct code for audit.</li>
 *   <li>{@code STUDENT_NOT_ENROLLED} — student is not enrolled in the
 *       session's section on its date (422).</li>
 *   <li>{@code EDIT_WINDOW_EXPIRED} — TEACHER attempts to edit after
 *       24h of {@code closed_at} (403).</li>
 *   <li>{@code FORCED_STATUS_FORBIDDEN} — non-admin sent a
 *       {@code forcedStatus} override on check-in (403).</li>
 *   <li>{@code OCCURRED_AT_DRIFT} — {@code occurredAt} too far in the
 *       future (anti-clock-skew, 400).</li>
 *   <li>{@code RECORD_EMPTY_PATCH} — {@code PUT /records/{id}} body
 *       contains no fields (400).</li>
 * </ul>
 */
public final class AttendanceErrorCodes {

	/** 409 — write against a CLOSED session. */
	public static final String SESSION_CLOSED = "SESSION_CLOSED";

	/** 409 — duplicate ACTIVE session for {@code (section, occurredOn, slot)}. */
	public static final String SESSION_ALREADY_OPEN = "SESSION_ALREADY_OPEN";

	/** 401 — QR token signature/type/format/claims invalid. */
	public static final String QR_INVALID = "QR_INVALID";

	/** 410 — QR token was revoked. */
	public static final String QR_EXPIRED = "QR_EXPIRED";

	/**
	 * Internal code logged when the QR's {@code tenant_id} claim does
	 * not match the bearer's current tenant. Surfaced to the client
	 * as {@code RESOURCE_NOT_FOUND} (anti-enumeration), but kept here
	 * for audit + alerting.
	 */
	public static final String QR_TENANT_MISMATCH = "QR_TENANT_MISMATCH";

	/** 422 — student not enrolled in the session's section. */
	public static final String STUDENT_NOT_ENROLLED = "STUDENT_NOT_ENROLLED";

	/**
	 * 422 — the student has no ACTIVE enrollment in any section. Surfaced
	 * by the manual check-in flow (BE-6.8) when the auxiliary picks a
	 * student by name+filters but the system cannot resolve which session
	 * to assign them to (no current section). The recovery hint is to
	 * register an enrollment first via the {@code students} module.
	 */
	public static final String STUDENT_NO_ACTIVE_ENROLLMENT = "STUDENT_NO_ACTIVE_ENROLLMENT";

	/** 403 — TEACHER edit attempt outside the 24h window. */
	public static final String EDIT_WINDOW_EXPIRED = "EDIT_WINDOW_EXPIRED";

	/** 403 — non-admin sent a {@code forcedStatus} override. */
	public static final String FORCED_STATUS_FORBIDDEN = "FORCED_STATUS_FORBIDDEN";

	/** 400 — {@code occurredAt} drift outside tolerance. */
	public static final String OCCURRED_AT_DRIFT = "OCCURRED_AT_DRIFT";

	/** 400 — empty PATCH body on update record. */
	public static final String RECORD_EMPTY_PATCH = "RECORD_EMPTY_PATCH";

	/** 400 — manual transition not in {@code legalManualTransitions()}. */
	public static final String RECORD_ILLEGAL_TRANSITION = "RECORD_ILLEGAL_TRANSITION";

	/** 400 — record already has a justification submitted (BE-18.5). */
	public static final String RECORD_ALREADY_JUSTIFIED = "RECORD_ALREADY_JUSTIFIED";

	/** 400 — record has no pending justification to approve/reject (BE-18.5). */
	public static final String RECORD_NO_PENDING_JUSTIFICATION = "RECORD_NO_PENDING_JUSTIFICATION";

	private AttendanceErrorCodes() {
	}
}
