package com.edushift.modules.academic.classrooms.exception;

import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a classroom lookup fails inside the current tenant.
 * Anti-enumeration: callers receive 404 / CLASSROOM_NOT_FOUND.
 */
public class ClassroomNotFoundException extends BusinessException {

	public ClassroomNotFoundException(String message) {
		super("CLASSROOM_NOT_FOUND", message);
	}
}