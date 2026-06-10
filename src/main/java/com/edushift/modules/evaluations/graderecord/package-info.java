/**
 * <strong>evaluations.graderecord sub-module</strong> — score (or
 * qualitative literal) registered by a teacher for a single
 * {@link com.edushift.modules.students.entity.Student} against a single
 * {@link com.edushift.modules.evaluations.entity.Evaluation}. Sprint 5B
 * / BE-5B.3.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (evaluation, student)} is unique on non-deleted rows
 *       (DB partial unique index). The service uses upsert semantics so
 *       re-posting the same pair updates the existing row instead of
 *       failing.</li>
 *   <li>Per-scale shape:
 *       <ul>
 *         <li>{@code SCORE_0_20} → numeric {@code score} in [0, 20].</li>
 *         <li>{@code LITERAL_AD} → {@code literal} in {AD, A}.</li>
 *         <li>{@code LITERAL_NA} → {@code literal} in {NA, A}.</li>
 *         <li>{@code LITERAL_A_B_C_D} → {@code literal} in {A, B, C, D}.</li>
 *       </ul>
 *       Cross-shape submissions raise {@code GRADE_VALUE_SHAPE_MISMATCH}.</li>
 *   <li>Writes are accepted only when the parent evaluation is in
 *       {@code DRAFT} or {@code PUBLISHED}; {@code CLOSED} is read-only
 *       and the service throws {@code GRADE_EVAL_CLOSED} (409).</li>
 *   <li>The student must be ACTIVE-enrolled in the assignment's section
 *       on the evaluation's {@code scheduledDate}; otherwise
 *       {@code GRADE_STUDENT_NOT_ENROLLED} (409).</li>
 *   <li>Bulk endpoint is atomic (all-or-nothing): a single invalid row
 *       aborts the whole transaction with
 *       {@code GRADE_BULK_INVALID_ROW}.</li>
 * </ul>
 *
 * <h3>Downstream</h3>
 * <ul>
 *   <li>BE-5B.4 — plugs real {@code gradeCount} into
 *       {@code EvaluationResponse} via
 *       {@link com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository#countByEvaluation}.</li>
 *   <li>FE-5B.4 grade-book matrix.</li>
 *   <li>{@code reports} (Phase 9) for tenant-wide academic analytics.</li>
 * </ul>
 *
 * @see com.edushift.modules.evaluations.entity.Evaluation
 */
package com.edushift.modules.evaluations.graderecord;
