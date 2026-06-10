package com.edushift.modules.evaluations.dto;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lean projection used by
 * {@code GET /v1/academic/assignments/{assignmentUuid}/evaluations}.
 *
 * <p>Drives the assignment-scoped tab in FE-5B.1. Includes the
 * aggregate {@code gradeCount} so the FE can show "0/30 graded"
 * without a second fetch and can render a progress bar.</p>
 */
public record EvaluationListItem(
		UUID publicUuid,
		EvaluationKind kind,
		String name,
		BigDecimal weight,
		LocalDate scheduledDate,
		LocalDate dueDate,
		EvaluationScale scale,
		EvaluationStatus status,
		Long gradeCount,
		Boolean isActive,
		Instant createdAt,
		Instant updatedAt
) {
}
