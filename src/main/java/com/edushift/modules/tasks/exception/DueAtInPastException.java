package com.edushift.modules.tasks.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a task's {@code dueAt} is set to a past timestamp
 * (Sprint 7a / BE-7a.2). The grace period is a future feature
 * (DEBT-7A-13).
 */
public class DueAtInPastException extends BusinessException {

	public DueAtInPastException() {
		super(TasksErrorCodes.DUE_AT_IN_PAST,
				"dueAt must not be in the past.");
	}
}
