package com.edushift.modules.tasks.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a task lifecycle transition is requested but the
 * current state forbids it (BUG-2026-07-31-04). Example: trying to
 * publish an already-PUBLISHED task, or archiving a DRAFT.
 */
public class InvalidTaskStateException extends BusinessException {

	private InvalidTaskStateException(String code, String message) {
		super(code, message);
	}

	public static InvalidTaskStateException notDraft(String currentState) {
		return new InvalidTaskStateException(
				TasksErrorCodes.TASK_INVALID_STATE,
				"Task is not in DRAFT state (current: " + currentState + ").");
	}

	public static InvalidTaskStateException notPublished(String currentState) {
		return new InvalidTaskStateException(
				TasksErrorCodes.TASK_INVALID_STATE,
				"Task is not in PUBLISHED state (current: " + currentState + ").");
	}

	public static InvalidTaskStateException alreadyArchived() {
		return new InvalidTaskStateException(
				TasksErrorCodes.TASK_INVALID_STATE,
				"Task is already ARCHIVED.");
	}
}
