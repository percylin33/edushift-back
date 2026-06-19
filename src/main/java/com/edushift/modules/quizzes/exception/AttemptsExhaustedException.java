package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * Thrown when a student tries to start a new attempt but already
 * used the {@code attempts_allowed} quota for the quiz
 * (Sprint 7b / BE-7b.2).
 *
 * <p>Maps to HTTP 409.
 */
public class AttemptsExhaustedException extends ConflictException {

	public AttemptsExhaustedException(int allowed, int consumed) {
		super(QuizzesErrorCodes.ATTEMPTS_EXHAUSTED,
				"Quiz attempt quota exhausted (allowed=" + allowed
						+ ", consumed=" + consumed + ").");
	}
}
