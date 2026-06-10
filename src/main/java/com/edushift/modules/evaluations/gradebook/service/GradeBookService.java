package com.edushift.modules.evaluations.gradebook.service;

import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import java.util.UUID;

/**
 * On-the-fly grade book aggregate (Sprint 5B / BE-5B.4 / ADR-5B.9).
 *
 * <p>Scope is one {@code TeacherAssignment} (i.e. one teacher × one
 * section × one course × one academic period). The aggregate composes
 * three sources:</p>
 * <ul>
 *   <li>The active {@code StudentEnrollment}s of the assignment's
 *       section — drives the rows of the matrix.</li>
 *   <li>All non-deleted {@code Evaluation}s under the assignment —
 *       drives the columns.</li>
 *   <li>All {@code GradeRecord}s pointing at any of those evaluations
 *       — drives the cells.</li>
 * </ul>
 *
 * <h3>Weighted average</h3>
 * For each student, the average is computed over the subset of
 * evaluations that are simultaneously:
 * <ol>
 *   <li>{@code status = PUBLISHED} or {@code status = CLOSED}
 *       (DRAFT evaluations are excluded — the grade book reflects
 *       what the teacher has committed, not work-in-progress).</li>
 *   <li>{@code scale = SCORE_0_20} (numeric) — literal-scale
 *       evaluations are excluded from the numeric average. If
 *       <em>all</em> a student's published evaluations are
 *       literal-scale (e.g. all {@code kind=RUBRIC}), the average is
 *       {@code null} per ADR-5B.4.</li>
 *   <li>The student has a recorded score (the cell exists and
 *       {@code score != null}). Missing cells short-circuit the
 *       student out of that evaluation's contribution but do not
 *       penalise the rest of the average.</li>
 * </ol>
 *
 * <p>The formula is the standard weighted mean:
 * {@code (Σ score_i × weight_i) / (Σ weight_i)}. If the denominator
 * is zero (no qualifying evaluation), the result is {@code null}.</p>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <caption>Grade book error codes</caption>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>{@code teacherAssignmentUuid} unknown for the active tenant
 *           (incl. cross-tenant).</td></tr>
 * </table>
 *
 * <p>An assignment with no enrolled students or no evaluations does
 * <strong>not</strong> error — it returns the response with empty
 * {@code students[]} / {@code evaluations[]} / {@code cells[]}. The
 * FE handles the empty state.</p>
 */
public interface GradeBookService {

    GradeBookResponse buildGradeBook(UUID teacherAssignmentPublicUuid);
}
