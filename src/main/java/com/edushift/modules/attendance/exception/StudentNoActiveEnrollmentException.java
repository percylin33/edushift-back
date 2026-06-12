package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * 422 — the picked student has no ACTIVE enrollment in any section
 * (Sprint 6 / BE-6.8 manual fallback).
 *
 * <p>Surfaced by {@code POST /attendance/manual-check-in} when the
 * auxiliary picks a student by name + filters but the backend cannot
 * resolve which section (and therefore which session) the check-in
 * should land on. Distinct from {@code STUDENT_NOT_ENROLLED}, which
 * fires when the student exists and IS enrolled, but in a different
 * section than the session the scan targets.
 */
public class StudentNoActiveEnrollmentException extends BusinessException {

	public StudentNoActiveEnrollmentException(String message) {
		super(AttendanceErrorCodes.STUDENT_NO_ACTIVE_ENROLLMENT, message);
	}
}
