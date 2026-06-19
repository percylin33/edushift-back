package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a {@code lms_quiz_attempts} row cannot be found
 * within the bearer's tenant (Sprint 7b / BE-7b.2). Cross-tenant
 * access also resolves here as 404 (anti-enumeration).
 */
public class AttemptNotFoundException extends NotFoundException {

	public AttemptNotFoundException(String publicUuid) {
		super(QuizzesErrorCodes.ATTEMPT_NOT_FOUND,
				"Quiz attempt not found: " + publicUuid);
	}
}
