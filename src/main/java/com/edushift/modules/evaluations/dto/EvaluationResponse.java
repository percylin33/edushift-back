package com.edushift.modules.evaluations.dto;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.evaluations.entity.Evaluation}.
 * Returned by {@code GET /v1/academic/evaluations/{publicUuid}} and by
 * the create / update / lifecycle endpoints.
 *
 * <p>Includes compact references to the parent assignment, optional
 * unit and optional learning session so the FE can render breadcrumbs
 * and the "anclada a" hint without extra round-trips.</p>
 */
public record EvaluationResponse(
		UUID publicUuid,
		AssignmentRef assignment,
		UUID unitPublicUuid,
		UUID learningSessionPublicUuid,
		EvaluationKind kind,
		String name,
		String description,
		BigDecimal weight,
		LocalDate scheduledDate,
		LocalDate dueDate,
		EvaluationScale scale,
		EvaluationStatus status,
		Instant publishedAt,
		Instant closedAt,
		Boolean isActive,
		Long gradeCount,
		Instant createdAt,
		Instant updatedAt
) {

	/**
	 * Compact summary of the parent {@code TeacherAssignment}. The FE
	 * already knows the (section, course, period) tuple by virtue of
	 * landing on the assignment page, so we only echo the
	 * {@code assignmentUuid} plus a friendly label.
	 */
	public record AssignmentRef(
			UUID publicUuid,
			String label
	) {
	}
}
