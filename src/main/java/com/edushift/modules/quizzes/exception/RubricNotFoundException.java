package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a rubric row cannot be found within the bearer's
 * tenant during a {@code PATCH /quizzes/{uuid}/rubric} flow
 * (Sprint 7b / BE-7b.3). Anti-enumeration: cross-tenant access
 * also resolves here as 404.
 */
public class RubricNotFoundException extends NotFoundException {

	public RubricNotFoundException(String publicUuid) {
		super(QuizzesErrorCodes.RUBRIC_NOT_FOUND,
				"Rubric not found: " + publicUuid);
	}
}
