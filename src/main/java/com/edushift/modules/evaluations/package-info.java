/**
 * <strong>evaluations sub-module</strong> — graded items authored by a
 * teacher against a {@code TeacherAssignment}. Sprint 5B / BE-5B.1.
 *
 * <p>An {@code Evaluation} is the unit of grade bookkeeping in EduShift:
 * a TASK, QUIZ, EXAM, RUBRIC or COMPETENCY-tagged assessment with a
 * {@code scale} (numeric or literal), weight, due date, and a strict
 * lifecycle (DRAFT -> PUBLISHED -> CLOSED, ADR-5B.7). It hangs off a
 * {@code TeacherAssignment} (teacher × section × course × period) and
 * optionally anchors to a {@code Unit} (BE-5A.1) or
 * {@code LearningSession} (BE-5A.4) for pedagogical traceability.</p>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (assignment, lower(name))} is unique on non-deleted rows
 *       (case-insensitive — ADR-5B.1).</li>
 *   <li>{@code due_date >= scheduled_date} when both are present
 *       ({@code EVAL_DATE_INVERTED}, 400).</li>
 *   <li>{@code kind} and {@code scale} must be coherent — validated at
 *       the service layer ({@code EVAL_KIND_SCALE_MISMATCH}, 400).
 *       E.g. {@code EXAM} requires {@code SCORE_0_20}; {@code RUBRIC}
 *       requires one of the literal scales.</li>
 *   <li>Lifecycle is one-way: {@code DRAFT -> PUBLISHED -> CLOSED}.
 *       CLOSED is fully read-only (no edit, no delete, no grade write —
 *       BE-5B.3 enforces {@code GRADE_EVAL_CLOSED}).</li>
 *   <li>An evaluation with active {@code GradeRecord}s cannot be hard
 *       soft-deleted ({@code EVAL_HAS_GRADES}, 409). Deactivation
 *       ({@code is_active=false}) is always allowed.</li>
 * </ul>
 *
 * <h3>Downstream</h3>
 * <ul>
 *   <li>{@code evaluations.rubrics} (BE-5B.2) — M:N link via
 *       {@code evaluation_rubric} (BE-5B.4). Constraint 0..1 rubrics per
 *       evaluation.</li>
 *   <li>{@code evaluations.grade-records} (BE-5B.3) — child rows that
 *       hold the per-student score/literal, with the evaluation's scale
 *       determining the payload shape.</li>
 *   <li>{@code reports} (Fase 9) will aggregate evaluations by
 *       assignment/section for the grade book export.</li>
 * </ul>
 */
package com.edushift.modules.evaluations;
