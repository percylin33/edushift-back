package com.edushift.modules.attendance.exception;

import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * 422 — the scanned student is not enrolled in the section the session
 * is bound to, on the session's date (Sprint 6 / BE-6.2).
 *
 * <p>Distinct from {@code RESOURCE_NOT_FOUND}: the student exists in
 * the tenant but does not belong to this section. Bubbling up
 * {@code STUDENT_NOT_ENROLLED} (instead of a generic 404) lets the FE
 * show "El alumno no pertenece a esta seccion" instead of "alumno no
 * existe".
 */
public class StudentNotEnrolledException extends BusinessException {

	public StudentNotEnrolledException(String message) {
		super(AttendanceErrorCodes.STUDENT_NOT_ENROLLED, message);
	}
}
