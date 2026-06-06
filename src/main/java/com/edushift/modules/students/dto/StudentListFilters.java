package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.EnrollmentStatus;
import java.util.UUID;

/**
 * Query-time filters for {@code GET /v1/students}.
 *
 * <p>{@code null} means "no filter on this dimension". The service
 * composes them as {@code AND} predicates via JPA Specifications.
 *
 * <ul>
 *   <li>{@code search} — case-insensitive substring against
 *       {@code firstName}, {@code lastName}, and {@code documentNumber}
 *       (the same fields admin tooling typically lets you search on).</li>
 *   <li>{@code enrollmentStatus} — exact equality on the institution-
 *       wide lifecycle ({@code Student.enrollmentStatus}).</li>
 *   <li>{@code gradeLevelId} — placeholder accepted for forward-
 *       compatibility; ignored by the service (logged at debug).</li>
 *   <li>{@code currentSectionPublicUuid} — added in BE-4.8: keeps only
 *       students that have an ACTIVE {@code StudentEnrollment} for the
 *       given section. Joins through {@code StudentEnrollment}.</li>
 *   <li>{@code currentAcademicYearPublicUuid} — added in BE-4.8: keeps
 *       only students that have an ACTIVE {@code StudentEnrollment}
 *       for the given academic year. Combines with
 *       {@code currentSectionPublicUuid} when both are set.</li>
 * </ul>
 */
public record StudentListFilters(
		String search,
		EnrollmentStatus enrollmentStatus,
		String gradeLevelId,
		UUID currentSectionPublicUuid,
		UUID currentAcademicYearPublicUuid
) {

	public static StudentListFilters empty() {
		return new StudentListFilters(null, null, null, null, null);
	}
}
