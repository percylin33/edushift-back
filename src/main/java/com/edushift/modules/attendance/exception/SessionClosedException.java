package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * 409 — write attempted against an {@code AttendanceSession} in
 * {@code CLOSED} state (Sprint 6 / BE-6.2).
 *
 * <p>Triggered by {@code POST /attendance/check-in} and
 * {@code POST /attendance/sessions/{id}/close} when called twice on
 * the same session.
 */
public class SessionClosedException extends ConflictException {

	public SessionClosedException(String message) {
		super(AttendanceErrorCodes.SESSION_CLOSED, message);
	}
}
