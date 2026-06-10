package com.edushift.modules.evaluations.graderecord.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Compact projection used in list endpoints
 * ({@code GET /evaluations/{uuid}/grade-records}).
 *
 * <p>Drops the parent evaluation snapshot (the caller always knows the
 * scope when listing per-evaluation) and the recorded-by metadata to
 * keep the payload small for the grade book matrix.
 */
public record GradeRecordListItem(

        UUID publicUuid,
        UUID studentPublicUuid,
        String studentFirstName,
        String studentLastName,
        String studentSecondLastName,

        BigDecimal score,
        String literal,
        String comments,

        Instant recordedAt,
        Boolean isActive,

        Instant createdAt,
        Instant updatedAt
) {
}
