package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * Thrown when a grading action is requested but the attempt has
 * not been submitted yet (Sprint 7b / BE-7b.2). The grading queue
 * only contains attempts in {@code SUBMITTED} or {@code AUTO_GRADED}
 * status.
 *
 * <p>Maps to HTTP 409.
 */
public class AttemptNotSubmittedException extends ConflictException {

	public AttemptNotSubmittedException(String currentState) {
		super(QuizzesErrorCodes.ATTEMPT_NOT_SUBMITTED,
				"Attempt must be SUBMITTED (or AUTO_GRADED) before grading; current: "
						+ currentState + ".");
	}
}
