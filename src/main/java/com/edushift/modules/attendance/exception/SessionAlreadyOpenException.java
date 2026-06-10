package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * 409 — there is already an ACTIVE {@code AttendanceSession} for the
 * triple {@code (section, occurredOn, slot)} (Sprint 6 / BE-6.2).
 *
 * <p>The DB partial unique index
 * {@code uk_attendance_sessions_section_day_slot_active} also enforces
 * this; the service does an explicit pre-check so the error message is
 * actionable and we never surface a generic constraint violation.
 */
public class SessionAlreadyOpenException extends ConflictException {

	public SessionAlreadyOpenException(String message) {
		super(AttendanceErrorCodes.SESSION_ALREADY_OPEN, message);
	}
}
