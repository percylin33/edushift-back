package com.edushift.modules.evaluations.graderecord.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload for "edit an existing grade record by its public UUID".
 *
 * <p>Sent against {@code PUT /v1/academic/grade-records/{publicUuid}}. The
 * (evaluation, student) pair is immutable: the service will reject any
 * attempt to retarget a grade. To "move" a grade from one student to
 * another, the teacher must DELETE the existing record and POST a new one.
 *
 * <p>Patch semantics: any non-null field replaces the existing value.
 * {@code null} fields are ignored (the existing value is preserved). This
 * differs from {@link CreateGradeRecordRequest} where the payload reflects
 * the full intended state.
 *
 * @param score    new numeric score, or null to leave unchanged.
 * @param literal  new qualitative literal, or null to leave unchanged.
 * @param comments new commentary; pass empty string {@code ""} to clear.
 */
public record UpdateGradeRecordRequest(

        @DecimalMin(value = "0.00", message = "score must be >= 0.00")
        @DecimalMax(value = "20.00", message = "score must be <= 20.00")
        BigDecimal score,

        @Size(min = 1, max = 8, message = "literal length out of range")
        String literal,

        @Size(max = 1000, message = "comments too long")
        String comments
) {
    public boolean isEmpty() {
        return score == null && literal == null && comments == null;
    }
}
