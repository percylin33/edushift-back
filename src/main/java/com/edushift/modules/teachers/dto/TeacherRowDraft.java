package com.edushift.modules.teachers.dto;

/**
 * One row of a teacher bulk-import spreadsheet, in its raw, parsed shape
 * (Sprint cierre-B / F7).
 *
 * <p>Fields mirror {@link CreateTeacherRequest} except for
 * {@code specializations} which is a comma-separated string in the
 * spreadsheet and parsed by the runner. {@code metadata} is left out
 * of the importable columns for V1 (admins can fill it in via the
 * single-POST path after import).</p>
 */
public record TeacherRowDraft(
		int rowNumber,
		com.edushift.modules.students.entity.DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String secondLastName,
		java.time.LocalDate birthDate,
		com.edushift.modules.students.entity.Gender gender,
		String email,
		String phone,
		String title,
		String specializationsRaw,
		java.time.LocalDate hireDate,
		com.edushift.modules.teachers.entity.EmploymentStatus employmentStatus
) {
}