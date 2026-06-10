package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.GoneException;

/**
 * 410 — the QR was issued by us but later revoked (lost / rotated /
 * admin-revoked) (Sprint 6 / BE-6.2).
 *
 * <p>HTTP 410 (instead of 401) signals "the resource was here, but is
 * gone now": idiomatic for revoked credentials. Distinct from
 * {@link QrInvalidException} (forged or never-issued tokens).
 */
public class QrExpiredException extends GoneException {

	public QrExpiredException(String message) {
		super(AttendanceErrorCodes.QR_EXPIRED, message);
	}
}
