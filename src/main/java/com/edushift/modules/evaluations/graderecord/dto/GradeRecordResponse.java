package com.edushift.modules.evaluations.graderecord.dto;

import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full representation of a {@code GradeRecord} returned by the API.
 *
 * <p>Includes a denormalized snapshot of the parent evaluation scale and
 * status: callers (FE-5B.4 grade book, BE-5B.4 reports) need both to
 * decide rendering and to gate further writes (CLOSED → read-only) without
 * an extra round-trip.
 */
public record GradeRecordResponse(

        UUID publicUuid,
        EvaluationRef evaluation,
        StudentRef student,

        BigDecimal score,
        String literal,
        String comments,

        Instant recordedAt,
        UUID recordedByUserId,
        Boolean isActive,

        Instant createdAt,
        Instant updatedAt
) {

    public record EvaluationRef(
            UUID publicUuid,
            String name,
            EvaluationScale scale,
            EvaluationStatus status
    ) {
    }

    public record StudentRef(
            UUID publicUuid,
            String firstName,
            String lastName,
            String secondLastName
    ) {
    }
}
