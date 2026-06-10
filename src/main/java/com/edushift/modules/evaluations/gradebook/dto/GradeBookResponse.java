package com.edushift.modules.evaluations.gradebook.dto;

import java.util.List;
import java.util.UUID;

/**
 * Aggregate response for {@code GET /v1/teacher-assignments/{uuid}/gradebook}
 * (Sprint 5B / BE-5B.4).
 *
 * <p>The grade book is a matrix {@code Student × Evaluation} computed
 * on-the-fly (ADR-5B.9: no materialised table). Three lists drive the
 * FE rendering:</p>
 * <ul>
 *   <li>{@link #students} — rows, with the per-row weighted average.</li>
 *   <li>{@link #evaluations} — columns, with metadata.</li>
 *   <li>{@link #cells} — non-empty intersections only; missing cells
 *       represent ungraded students for that evaluation.</li>
 * </ul>
 *
 * <p>The {@code (sectionPublicUuid, sectionName, coursePublicUuid,
 * courseName)} block denormalises the parent assignment so the FE can
 * render the page header without a second round-trip.</p>
 *
 * <h3>Shape vs spec note</h3>
 * <p>The Sprint 5B spec sketched the response with a single flat
 * {@code evaluations[]} array containing per-cell scores. We split it
 * into three lists ({@code students}, {@code evaluations},
 * {@code cells}) to (a) avoid duplicating evaluation metadata for
 * every student and (b) make the matrix naturally sparse — the FE
 * iterates {@code cells} and renders empty cells as "—" without a
 * special-case "score is null and we don't know why" path. The
 * weighted average is per-student (inside {@link GradeBookStudentEntry})
 * so the FE renders the totals column without a re-aggregation pass.</p>
 */
public record GradeBookResponse(
        UUID assignmentPublicUuid,
        UUID sectionPublicUuid,
        String sectionName,
        UUID coursePublicUuid,
        String courseName,
        List<GradeBookStudentEntry> students,
        List<GradeBookEvaluationEntry> evaluations,
        List<GradeBookCellEntry> cells
) {
}
