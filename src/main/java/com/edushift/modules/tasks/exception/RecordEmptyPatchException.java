package com.edushift.modules.tasks.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a PATCH body for a task is empty.
 */
public class RecordEmptyPatchException extends BusinessException {

	public RecordEmptyPatchException() {
		super(TasksErrorCodes.RECORD_EMPTY_PATCH,
				"PATCH body must contain at least one field to update.");
	}
}
