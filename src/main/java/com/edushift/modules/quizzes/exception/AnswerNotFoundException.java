package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a {@code lms_quiz_answers} row cannot be found
 * within the bearer's tenant (Sprint 7b / BE-7b.2).
 */
public class AnswerNotFoundException extends NotFoundException {

	public AnswerNotFoundException(String publicUuid) {
		super(QuizzesErrorCodes.ANSWER_NOT_FOUND,
				"Quiz answer not found: " + publicUuid);
	}
}
