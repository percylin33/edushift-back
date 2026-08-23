package com.edushift.modules.family.dto;

import com.edushift.modules.students.entity.EnrollmentStatus;
import java.util.UUID;

/** A privacy-safe child summary exposed only to the linked parent. */
public record FamilyChildSummary(
		UUID publicUuid,
		String firstName,
		String lastName,
		String fullName,
		EnrollmentStatus enrollmentStatus
) {
}
