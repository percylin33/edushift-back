package com.edushift.modules.evaluations.gradebook.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in the grade book matrix (Sprint 5B / BE-5B.4).
 *
 * <p>The student is identified by {@code publicUuid} and a denormalised
 * {@code fullName} for label rendering. {@code weightedAverage} is the
 * per-student weighted average of all grades whose parent evaluation is
 * in {@code PUBLISHED} or {@code CLOSED} status with a numeric scale
 * ({@code SCORE_0_20}); see {@code GradeBookService} for the exact
 * computation. The value is {@code null} when there is no completed
 * numeric evaluation to average over (e.g. all evaluations are
 * {@code DRAFT}, or all are literal-scale).</p>
 */
public record GradeBookStudentEntry(
        UUID publicUuid,
        String fullName,
        BigDecimal weightedAverage
) {
}
