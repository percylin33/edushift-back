package com.edushift.modules.evaluations.graderecord.error;

/**
 * Stable error codes raised by the {@code evaluations.grade-records}
 * sub-module (Sprint 5B / BE-5B.3).
 *
 * <p>Codes are part of the public API contract: keep them stable, append
 * new ones at the bottom. Removing or renaming a code is a breaking change.
 */
public final class GradeRecordErrorCodes {

    /** Both {@code score} and {@code literal} are null in the request. */
    public static final String GRADE_VALUE_REQUIRED = "GRADE_VALUE_REQUIRED";

    /** {@code score} is out of the allowed range for the parent scale. */
    public static final String GRADE_SCORE_OUT_OF_RANGE = "GRADE_SCORE_OUT_OF_RANGE";

    /** {@code literal} does not belong to the catalogue of the parent scale. */
    public static final String GRADE_LITERAL_INVALID = "GRADE_LITERAL_INVALID";

    /** Parent evaluation is in CLOSED status — writes are rejected. */
    public static final String GRADE_EVAL_CLOSED = "GRADE_EVAL_CLOSED";

    /** Student is not ACTIVE-enrolled in the assignment's section at the evaluation date. */
    public static final String GRADE_STUDENT_NOT_ENROLLED = "GRADE_STUDENT_NOT_ENROLLED";

    /** A row in a bulk payload is invalid; aborts the whole batch. */
    public static final String GRADE_BULK_INVALID_ROW = "GRADE_BULK_INVALID_ROW";

    /** {@code score} field was sent for a LITERAL_* scale (or vice-versa). */
    public static final String GRADE_VALUE_SHAPE_MISMATCH = "GRADE_VALUE_SHAPE_MISMATCH";

    private GradeRecordErrorCodes() {
        // utility
    }
}
