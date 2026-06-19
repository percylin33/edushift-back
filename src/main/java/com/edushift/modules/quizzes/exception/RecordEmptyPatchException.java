package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.BadRequestException;

/**
 * Thrown when a PATCH request arrives with no fields set
 * (Sprint 7b / BE-7b.1). Mirrors the {@code lms_tasks}
 * pattern.
 */
public class RecordEmptyPatchException extends BadRequestException {

	public RecordEmptyPatchException() {
		super(QuizzesErrorCodes.QUIZ_RECORD_EMPTY_PATCH,
				"PATCH body must include at least one non-null field.");
	}
}
