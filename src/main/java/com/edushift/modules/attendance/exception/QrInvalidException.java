package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.UnauthorizedException;

/**
 * 401 — the QR token is malformed, has the wrong signature, or has
 * been minted with a different {@code typ} (Sprint 6 / BE-6.2).
 *
 * <p>Distinct from {@link QrExpiredException}: this means the token
 * was never issued by us (or was issued with a now-rotated secret),
 * not that it was revoked.
 */
public class QrInvalidException extends UnauthorizedException {

	public QrInvalidException(String message) {
		super(AttendanceErrorCodes.QR_INVALID, message);
	}
}
