package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.EnrollmentStatus;

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
 *   <li>{@code enrollmentStatus} — exact equality.</li>
 *   <li>{@code gradeLevelId} — placeholder for Sprint 4. Accepted by
 *       the controller for forward-compatibility but ignored by the
 *       service for now (logged at debug).</li>
 * </ul>
 */
public record StudentListFilters(
		String search,
		EnrollmentStatus enrollmentStatus,
		String gradeLevelId
) {

	public static StudentListFilters empty() {
		return new StudentListFilters(null, null, null);
	}
}
