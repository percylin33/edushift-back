package com.edushift.modules.teachers.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TeacherResponse(
		UUID publicUuid,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String secondLastName,
		LocalDate birthDate,
		Gender gender,
		String email,
		String phone,
		String title,
		List<String> specializations,
		LocalDate hireDate,
		EmploymentStatus employmentStatus,
		UUID userPublicUuid,
		Map<String, Object> metadata,
		Instant createdAt,
		Instant updatedAt
) {
}
