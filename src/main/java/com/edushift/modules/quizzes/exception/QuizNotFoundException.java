package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a quiz row cannot be found within the bearer's tenant
 * (Sprint 7b / BE-7b.1). Anti-enumeration: cross-tenant access also
 * resolves here as 404.
 */
public class QuizNotFoundException extends NotFoundException {

	public QuizNotFoundException(String publicUuid) {
		super(QuizzesErrorCodes.QUIZ_NOT_FOUND,
				"Quiz not found: " + publicUuid);
	}
}
