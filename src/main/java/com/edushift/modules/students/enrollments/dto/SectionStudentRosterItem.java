package com.edushift.modules.students.enrollments.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-student row of {@code GET /v1/academic/sections/{uuid}/students}.
 *
 * <p>The roster endpoint returns the section's <em>currently active</em>
 * students. The status field is included for symmetry with the historic
 * filter (see service docs); the {@code active} flag is derived for
 * client convenience.</p>
 */
public record SectionStudentRosterItem(
		UUID enrollmentPublicUuid,

		UUID studentPublicUuid,
		String studentFullName,
		DocumentType documentType,
		String documentNumber,
		String email,

		LocalDate enrolledAt,
		LocalDate withdrawnAt,
		StudentEnrollmentStatus status,
		boolean active
) {
}
