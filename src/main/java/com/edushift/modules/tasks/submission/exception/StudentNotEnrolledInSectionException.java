package com.edushift.modules.tasks.submission.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BusinessException;
import java.util.UUID;

/**
 * Thrown when a student is not enrolled in the task's section.
 * Mapped to 422 Unprocessable Entity by the global handler.
 */
public class StudentNotEnrolledInSectionException extends BusinessException {

	public StudentNotEnrolledInSectionException(UUID studentPublicUuid, UUID sectionId) {
		super(TasksErrorCodes.STUDENT_NOT_ENROLLED_IN_SECTION,
				"Student " + studentPublicUuid
						+ " is not enrolled in section " + sectionId);
	}
}
