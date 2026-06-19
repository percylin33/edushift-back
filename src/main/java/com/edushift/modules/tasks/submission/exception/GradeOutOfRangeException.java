package com.edushift.modules.tasks.submission.exception;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BadRequestException;

public class GradeOutOfRangeException extends BadRequestException {

	public GradeOutOfRangeException(int grade) {
		super(TasksErrorCodes.GRADE_OUT_OF_RANGE,
				"grade must be in [0, 100], got " + grade);
	}
}
