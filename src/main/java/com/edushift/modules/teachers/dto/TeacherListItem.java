package com.edushift.modules.teachers.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import java.util.List;
import java.util.UUID;

public record TeacherListItem(
		UUID publicUuid,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String secondLastName,
		String email,
		String title,
		List<String> specializations,
		EmploymentStatus employmentStatus,
		boolean hasUserAccount
) {
}
