package com.edushift.modules.quizzes.exception;

import com.edushift.shared.exception.ForbiddenException;

/**
 * Thrown when a student (or a parent on-behalf) tries to start or
 * access an attempt for a quiz whose parent section they are not
 * actively enrolled in (Sprint 7b / BE-7b.2).
 *
 * <p>Resolves as 403 with a stable error code so the FE can render
 * "no matriculado en esta sección" without leaking the section's
 * existence to non-members (separate from the section 404 used
 * for cross-tenant lookups).
 */
public class StudentNotEnrolledException extends ForbiddenException {

	public StudentNotEnrolledException(String sectionPublicUuid) {
		super("STUDENT_NOT_ENROLLED_IN_SECTION",
				"Student is not actively enrolled in section: " + sectionPublicUuid);
	}
}
