package com.edushift.modules.evaluations.gradebook.dto;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One column header in the grade book matrix (Sprint 5B / BE-5B.4).
 *
 * <p>Evaluation metadata only — per-cell {@code score} / {@code literal}
 * live in {@link GradeBookCellEntry}. Splitting metadata from cells
 * keeps the payload size at <strong>O(N + M + N×M)</strong> instead of
 * <strong>O(N×M × meta-size)</strong>, which matters once a section
 * has 30+ evaluations on display.</p>
 */
public record GradeBookEvaluationEntry(
        UUID publicUuid,
        String name,
        EvaluationKind kind,
        EvaluationScale scale,
        EvaluationStatus status,
        BigDecimal weight,
        LocalDate scheduledDate
) {
}
