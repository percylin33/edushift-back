package com.edushift.modules.evaluations.graderecord.service;

import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.BulkGradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.CreateGradeRecordRequest;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordFilters;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordListItem;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.dto.UpdateGradeRecordRequest;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for {@code evaluations.graderecord} (Sprint 5B / BE-5B.3).
 *
 * <h3>Error contract</h3>
 * The implementation raises the {@code GRADE_*} codes from
 * {@link com.edushift.modules.evaluations.graderecord.error.GradeRecordErrorCodes},
 * mapped to HTTP status codes by
 * {@link com.edushift.shared.exception.GlobalExceptionHandler}:
 * <ul>
 *   <li>400 — payload shape ({@code GRADE_VALUE_REQUIRED},
 *       {@code GRADE_VALUE_SHAPE_MISMATCH}, {@code GRADE_SCORE_OUT_OF_RANGE},
 *       {@code GRADE_LITERAL_INVALID}).</li>
 *   <li>404 — evaluation / student not found under the active tenant.</li>
 *   <li>409 — business gate ({@code GRADE_EVAL_CLOSED},
 *       {@code GRADE_STUDENT_NOT_ENROLLED}, {@code GRADE_BULK_INVALID_ROW}).</li>
 * </ul>
 */
public interface GradeRecordService {

    /**
     * Lists grades against a single evaluation, optionally filtered.
     *
     * @param evaluationPublicUuid public UUID of the parent evaluation.
     * @param filters              optional filters; {@code null} means
     *                             "no filter".
     * @return list of grades; never {@code null}.
     */
    List<GradeRecordListItem> listGrades(
            UUID evaluationPublicUuid,
            GradeRecordFilters filters);

    /**
     * Returns the full DTO for a grade by its public UUID.
     */
    GradeRecordResponse getGrade(UUID publicUuid);

    /**
     * Upserts a single grade for a {@code (evaluation, student)} pair.
     * If a row already exists for the pair (and is not soft-deleted),
     * its score / literal / comments / recordedAt / recordedByUserId
     * are updated; otherwise a new row is inserted.
     *
     * @return the persisted DTO.
     */
    GradeRecordResponse upsertGrade(
            UUID evaluationPublicUuid,
            CreateGradeRecordRequest request);

    /**
     * Edits an existing grade row by its public UUID. Patch semantics:
     * non-null fields replace, null fields are ignored. The
     * {@code (evaluation, student)} pair is immutable.
     */
    GradeRecordResponse updateGrade(
            UUID publicUuid,
            UpdateGradeRecordRequest request);

    /**
     * Atomic bulk upsert: validates every row up-front and persists in
     * a single transaction. One invalid row aborts the whole batch
     * with {@code GRADE_BULK_INVALID_ROW}.
     */
    BulkGradeRecordResponse bulkUpsert(
            UUID evaluationPublicUuid,
            BulkGradeRecordRequest request);

    /**
     * Soft-deletes a grade row. The {@code @SQLDelete} on
     * {@link com.edushift.modules.evaluations.graderecord.entity.GradeRecord}
     * sets {@code deleted = true} + {@code deleted_at = NOW()}.
     */
    void deleteGrade(UUID publicUuid);
}
