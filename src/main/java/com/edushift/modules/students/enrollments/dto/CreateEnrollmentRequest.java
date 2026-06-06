package com.edushift.modules.students.enrollments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/students/{studentUuid}/enrollments}.
 *
 * <p>The {@code student} is the path variable; the body anchors the
 * matricula to a {@code (section, academicYear)} pair plus the start
 * date. The service validates that the year of the request matches the
 * year of the section, that the date falls inside the year window, and
 * that the student does not already have an active row for the same
 * year.</p>
 *
 * @param sectionPublicUuid       target section
 * @param academicYearPublicUuid  target academic year (must equal
 *                                {@code section.academicYear})
 * @param enrolledAt              first day in the section; must be
 *                                inside the academic year window
 * @param notes                   optional admin notes
 */
public record CreateEnrollmentRequest(

		@NotNull(message = "sectionPublicUuid is required")
		UUID sectionPublicUuid,

		@NotNull(message = "academicYearPublicUuid is required")
		UUID academicYearPublicUuid,

		@NotNull(message = "enrolledAt is required")
		LocalDate enrolledAt,

		@Size(max = 1000, message = "notes too long")
		String notes
) {
}
