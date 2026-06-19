package com.edushift.modules.tasks.submission.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.ForbiddenException;
import java.util.UUID;

/**
 * Thrown when a parent attempts to submit on behalf of a student
 * they are not linked to in {@code users.parent_of_student}
 * (Sprint 7a / BE-7a.2, REQ-TSK-06).
 */
public class NotGuardianOfStudentException extends ForbiddenException {

	public NotGuardianOfStudentException(UUID parentPublicUuid, UUID studentPublicUuid) {
		super(TasksErrorCodes.NOT_GUARDIAN_OF_STUDENT,
				"Parent " + parentPublicUuid
						+ " is not linked to student " + studentPublicUuid);
	}
}
