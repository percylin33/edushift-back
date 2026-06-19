package com.edushift.modules.tasks.submission.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.ConflictException;

public class ResubmissionNotAllowedException extends ConflictException {

	public ResubmissionNotAllowedException() {
		super(TasksErrorCodes.RESUBMISSION_NOT_ALLOWED,
				"This task does not allow re-submissions.");
	}
}
