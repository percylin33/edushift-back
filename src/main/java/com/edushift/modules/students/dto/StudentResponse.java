package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Full projection of a {@link com.edushift.modules.students.entity.Student}.
 *
 * <p>Same shape as the list-item projection plus the heavier fields
 * (metadata, address, secondLastName, audit timestamps) — keeping the
 * two distinct lets the list endpoint ship lean rows while detail
 * pages get everything in one call.
 */
public record StudentResponse(
		UUID publicUuid,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String secondLastName,
		String fullName,
		LocalDate birthDate,
		Gender gender,
		String email,
		String phone,
		String address,
		EnrollmentStatus enrollmentStatus,
		LocalDate enrollmentDate,
		UUID userId,
		Map<String, Object> metadata,
		Instant createdAt,
		Instant updatedAt
) {
}
