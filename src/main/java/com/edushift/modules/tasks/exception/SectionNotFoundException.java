package com.edushift.modules.tasks.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a section is not visible to the bearer
 * (Sprint 7a / BE-7a.2). For the list endpoint we deliberately
 * surface this as 404 (not 403) so that no leak occurs; the
 * existence of the section itself is the precondition of the
 * operation, not the target.
 */
public class SectionNotFoundException extends NotFoundException {

	public SectionNotFoundException(String sectionPublicUuid) {
		super(TasksErrorCodes.SECTION_NOT_FOUND,
				"Section not found: " + sectionPublicUuid);
	}
}
