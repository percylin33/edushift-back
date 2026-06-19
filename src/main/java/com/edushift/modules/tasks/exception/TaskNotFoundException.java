package com.edushift.modules.tasks.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * Thrown when a task row cannot be found within the bearer's tenant
 * (Sprint 7a / BE-7a.2). Anti-enumeration: cross-tenant access also
 * resolves here.
 */
public class TaskNotFoundException extends NotFoundException {

	public TaskNotFoundException(String publicUuid) {
		super(TasksErrorCodes.TASK_NOT_FOUND,
				"Task not found: " + publicUuid);
	}
}
