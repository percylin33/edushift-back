package com.edushift.modules.evaluations.graderecord.dto;

import com.edushift.shared.validation.annotations.ValidUuid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload for "register a grade for a single student against an evaluation".
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code POST /v1/academic/evaluations/{uuid}/grade-records} (single upsert).</li>
 *   <li>Inside {@link BulkGradeRecordRequest} as one of the rows.</li>
 * </ul>
 *
 * <p>At least one of {@code score} / {@code literal} must be present (cross-field
 * check in the service: {@code GRADE_VALUE_REQUIRED}). The shape valid for the
 * payload depends on the parent {@code Evaluation.scale}; the service validates
 * the per-scale subset and emits {@code GRADE_SCORE_OUT_OF_RANGE} or
 * {@code GRADE_LITERAL_INVALID} if the shape doesn't match.
 *
 * @param studentPublicUuid public UUID (v4) of the {@code Student} the grade
 *                          targets. Required.
 * @param score             Numeric score for {@code SCORE_0_20} evaluations,
 *                          in [0, 20] with up to 2 decimals. Null for literal
 *                          scales.
 * @param literal           Qualitative literal for {@code LITERAL_*} scales.
 *                          Null for {@code SCORE_0_20}.
 * @param comments          Optional teacher commentary, max 1000 chars.
 */
public record CreateGradeRecordRequest(

        @NotBlank(message = "studentPublicUuid is required")
        @ValidUuid(message = "studentPublicUuid must be a valid UUID")
        String studentPublicUuid,

        @DecimalMin(value = "0.00", message = "score must be >= 0.00")
        @DecimalMax(value = "20.00", message = "score must be <= 20.00")
        BigDecimal score,

        @Size(min = 1, max = 8, message = "literal length out of range")
        String literal,

        @Size(max = 1000, message = "comments too long")
        String comments
) {
}
