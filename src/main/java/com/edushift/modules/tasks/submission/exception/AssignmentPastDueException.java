package com.edushift.modules.tasks.submission.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * Thrown when a submission is attempted after the task's
 * {@code dueAt} (Sprint 7a / BE-7a.2). The grace period is a
 * future feature (DEBT-7A-14).
 */
public class AssignmentPastDueException extends ConflictException {

	public AssignmentPastDueException() {
		super(TasksErrorCodes.ASSIGNMENT_PAST_DUE,
				"Assignment is past its due date.");
	}
}
