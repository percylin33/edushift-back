package com.edushift.modules.evaluations.graderecord.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for "register grades for N students against an evaluation in
 * a single atomic transaction" (ADR-5B.6).
 *
 * <p>All rows are validated up-front; if any row fails (per-scale shape,
 * student not enrolled, etc.), the whole batch is rejected with
 * {@code GRADE_BULK_INVALID_ROW} and an {@code index} field pointing at
 * the offending row. Persistence happens inside a single
 * {@code @Transactional} unit so a partial save is impossible
 * (all-or-nothing).
 *
 * <p>Hard cap of 200 rows per call: a section's grade book is bounded
 * (≤ 50 students typically), and 200 leaves headroom for "register the
 * whole school year for one student" use cases. Larger batches should
 * be split client-side or use the future async CSV import (DEBT-EVAL-2).
 *
 * @param records non-empty list of single-row payloads. Each row is
 *                bean-validated independently before the batch is processed.
 */
public record BulkGradeRecordRequest(

        @NotEmpty(message = "records cannot be empty")
        @Size(max = 200, message = "records cannot exceed 200 rows per request")
        @Valid
        List<CreateGradeRecordRequest> records
) {
}
