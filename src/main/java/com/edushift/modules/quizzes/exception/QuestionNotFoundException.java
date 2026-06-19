package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a quiz-question row cannot be found within the
 * bearer's tenant (Sprint 7b / BE-7b.1).
 */
public class QuestionNotFoundException extends NotFoundException {

	public QuestionNotFoundException(String publicUuid) {
		super(QuizzesErrorCodes.QUESTION_NOT_FOUND,
				"Question not found: " + publicUuid);
	}
}
