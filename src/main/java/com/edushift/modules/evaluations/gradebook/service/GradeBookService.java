package com.edushift.modules.evaluations.gradebook.service;

import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import java.util.UUID;

public interface GradeBookService {

	GradeBookResponse buildGradeBook(
			UUID teacherAssignmentPublicUuid,
			boolean includeWithdrawn,
			boolean includeTransferred
	);

	default GradeBookResponse buildGradeBook(UUID teacherAssignmentPublicUuid) {
		return buildGradeBook(teacherAssignmentPublicUuid, false, false);
	}

	byte[] exportXlsx(
			UUID teacherAssignmentPublicUuid,
			boolean includeWithdrawn,
			boolean includeTransferred
	);

	byte[] exportPdf(
			UUID teacherAssignmentPublicUuid,
			boolean includeWithdrawn,
			boolean includeTransferred
	);
}
