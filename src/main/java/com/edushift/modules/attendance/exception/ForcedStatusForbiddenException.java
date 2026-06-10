package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.ForbiddenException;

/**
 * 403 — a non-admin role tried to send {@code forcedStatus} on
 * {@code POST /attendance/check-in} (Sprint 6 / BE-6.2).
 *
 * <p>The {@code forcedStatus} field is an admin-only override that
 * bypasses the LATE/PRESENT computation. Sending it as a TEACHER is
 * always a privilege-escalation attempt.
 */
public class ForcedStatusForbiddenException extends ForbiddenException {

	public ForcedStatusForbiddenException(String message) {
		super(AttendanceErrorCodes.FORCED_STATUS_FORBIDDEN, message);
	}
}
