package com.edushift.modules.evaluations.gradebook.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One cell of the grade book matrix (Sprint 5B / BE-5B.4).
 *
 * <p>The cell is keyed by the {@code (studentPublicUuid,
 * evaluationPublicUuid)} pair. Either {@code score} or {@code literal}
 * is non-null (never both, never neither — that's the
 * {@code GRADE_VALUE_REQUIRED} invariant from BE-5B.3); for unscored
 * cells the FE should render an empty state and the cell is simply
 * omitted from the {@code cells} list.</p>
 *
 * <p>{@code recordedAt} is the timestamp the teacher last persisted
 * the grade — {@code created_at} on first persist, the latest
 * {@code updated_at} on subsequent edits.</p>
 */
public record GradeBookCellEntry(
        UUID studentPublicUuid,
        UUID evaluationPublicUuid,
        BigDecimal score,
        String literal,
        Instant recordedAt
) {
}
