package com.edushift.modules.tasks.submission.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.NotFoundException;

public class SubmissionNotFoundException extends NotFoundException {

	public SubmissionNotFoundException(String publicUuid) {
		super(TasksErrorCodes.SUBMISSION_NOT_FOUND,
				"Submission not found: " + publicUuid);
	}
}
