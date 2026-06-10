package com.edushift.modules.evaluations.graderecord.dto;

import java.util.List;

/**
 * Atomic outcome of a {@link BulkGradeRecordRequest}. Either the whole
 * batch succeeded — and {@code records} contains all the persisted rows
 * — or none of them did and the controller returned 400 with
 * {@code GRADE_BULK_INVALID_ROW} (this DTO is never used to report
 * failures).
 *
 * @param requested how many rows the client sent.
 * @param created   how many of those were inserts (vs upsert updates).
 * @param updated   how many of those were upsert updates (existing
 *                  {@code (evaluation, student)} pair).
 * @param records   the persisted rows in the same order as the request.
 */
public record BulkGradeRecordResponse(

        int requested,
        int created,
        int updated,
        List<GradeRecordResponse> records
) {
}
