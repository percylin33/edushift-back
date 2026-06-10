package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.ForbiddenException;

/**
 * 403 — a {@code TEACHER} attempted to edit an
 * {@link com.edushift.modules.attendance.entity.AttendanceRecord}
 * outside the 24h editing window after
 * {@code session.closedAt} (Sprint 6 / BE-6.2 / ADR-6.7).
 *
 * <p>{@code TENANT_ADMIN} has no window and never trips this
 * exception.
 */
public class EditWindowExpiredException extends ForbiddenException {

	public EditWindowExpiredException(String message) {
		super(AttendanceErrorCodes.EDIT_WINDOW_EXPIRED, message);
	}
}
