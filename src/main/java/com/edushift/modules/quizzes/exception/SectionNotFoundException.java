package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a section is not visible to the bearer
 * (Sprint 7b / BE-7b.1). Surfaces as 404 (anti-enumeration),
 * mirroring the {@code lms_tasks} convention.
 */
public class SectionNotFoundException extends NotFoundException {

	public SectionNotFoundException(String sectionPublicUuid) {
		super(QuizzesErrorCodes.SECTION_NOT_FOUND,
				"Section not found: " + sectionPublicUuid);
	}
}
