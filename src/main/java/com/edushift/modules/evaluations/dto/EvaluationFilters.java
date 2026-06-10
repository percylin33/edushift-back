package com.edushift.modules.evaluations.dto;

import com.edushift.modules.evaluations.entity.EvaluationStatus;

/**
 * Filter bag for
 * {@code GET /v1/academic/assignments/{assignmentUuid}/evaluations} and
 * the cross-tenant search
 * {@code GET /v1/academic/evaluations?status=...&from=...&to=...}.
 *
 * <p>All fields are optional. The service layer applies each present
 * filter as an AND conjunction; the controller maps an empty
 * {@code EvaluationFilters} to "all non-deleted evaluations visible
 * to the caller".</p>
 */
public record EvaluationFilters(
		EvaluationStatus status,
		Boolean isActive,
		java.time.LocalDate from,
		java.time.LocalDate to
) {
}
