package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lean projection for the students list view.
 *
 * <p>Drops the heavy fields ({@code metadata}, {@code address},
 * {@code secondLastName}, audit timestamps): table cells render dozens
 * of rows, no need to ship every column. The detail endpoint surfaces
 * everything when the user clicks through.
 */
public record StudentListItem(
		UUID publicUuid,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String fullName,
		String email,
		EnrollmentStatus enrollmentStatus,
		LocalDate enrollmentDate
) {
}
