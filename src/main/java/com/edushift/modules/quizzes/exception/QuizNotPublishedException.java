package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * Thrown when a student tries to start a quiz attempt but the
 * quiz is not in {@code PUBLISHED} state (Sprint 7b / BE-7b.2).
 *
 * <p>Maps to HTTP 409.
 */
public class QuizNotPublishedException extends ConflictException {

	public QuizNotPublishedException(String currentState) {
		super(QuizzesErrorCodes.QUIZ_NOT_PUBLISHED,
				"Quiz is not PUBLISHED (current: " + currentState + ").");
	}
}
