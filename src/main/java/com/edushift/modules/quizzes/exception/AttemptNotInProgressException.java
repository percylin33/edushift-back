package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * Thrown when an attempt operation is rejected because the
 * attempt is no longer {@code IN_PROGRESS}
 * (Sprint 7b / BE-7b.2). E.g. trying to autosave on a
 * {@code SUBMITTED} or {@code GRADED} attempt.
 *
 * <p>Maps to HTTP 409.
 */
public class AttemptNotInProgressException extends ConflictException {

	public AttemptNotInProgressException(String currentState) {
		super(QuizzesErrorCodes.ATTEMPT_NOT_IN_PROGRESS,
				"Attempt is not IN_PROGRESS (current: " + currentState + ").");
	}
}
