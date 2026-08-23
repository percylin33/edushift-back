package com.edushift.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-evaluation grade envelope surfaced at
 * {@code GET /api/v1/me/grades} (DEBT-STUDENT-PRIVACY / Fase 2).
 *
 * <p>One row per evaluation that has a recorded grade for the
 * authenticated student. Ordered by evaluation scheduled date desc,
 * so the FE renders "lo más reciente primero" without any client-side
 * sorting.</p>
 *
 * <p>{@code score} is the numeric value (0..{@code maxScore}) when the
 * evaluation uses numeric scoring. {@code literal} is the qualitative
 * letter (A/B/C/D or the rubric output) when the teacher applied a
 * rubric. Exactly one of the two is populated; the other is null.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeGradeResponse(
        UUID publicUuid,
        UUID evaluationPublicUuid,
        String evaluationName,
        String sectionName,
        String courseName,
        BigDecimal score,
        BigDecimal maxScore,
        String literal,
        String comments,
        Instant scheduledDate,
        Instant recordedAt
) {
}
