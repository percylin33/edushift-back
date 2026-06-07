/**
 * <strong>academic.competency sub-module</strong> — MINEDU-style competencies
 * and capacities scoped to a course (per-tenant editable). Sprint 5A / BE-5A.2.
 *
 * <p>Two aggregates live here together because they form a tight 1:N
 * tree (a {@code Capacity} only exists in the context of its parent
 * {@code Competency} — same convention as {@code Course} + {@code CourseLevel}
 * in BE-4.4):</p>
 *
 * <ul>
 *   <li>{@link com.edushift.modules.academic.competency.entity.Competency}
 *       — per course, ordered set, each carrying a {@code code} unique
 *       per course case-insensitive.</li>
 *   <li>{@link com.edushift.modules.academic.competency.entity.Capacity}
 *       — per competency, ordered set, same code/order invariants
 *       scoped to the parent.</li>
 * </ul>
 *
 * <h3>Per-tenant editable + on-demand seed</h3>
 * Both aggregates are fully CRUD-able by {@code TENANT_ADMIN}. The MINEDU
 * default catalog is loaded <strong>on-demand per course</strong> via
 * {@code POST /v1/academic/courses/{courseUuid}/competencies/seed-defaults}
 * (NOT during self-signup, because the seed depends on courses existing
 * first). The mapping from {@code course.code} to seed bundle lives in
 * {@code config/CompetencyDefaults} (currently MAT and COMU).
 *
 * <h3>Reorder strategy</h3>
 * Both {@code competencies} and {@code capacities} use the same two-pass
 * reorder mirrored from {@code GradeServiceImpl} (BE-4.2) and
 * {@code UnitServiceImpl} (BE-5A.1): park each affected row at a temp
 * ordinal above {@code maxOrdinal + 1000}, then assign the final
 * ordinal — avoids tripping the partial unique index without DEFERRED
 * constraints.
 *
 * <h3>Downstream</h3>
 * <ul>
 *   <li>{@code learning_sessions} (BE-5A.4) will reference both via
 *       {@code competency_ids[]} / {@code capacity_ids[]}; cross-course
 *       references will be rejected with
 *       {@code COMPETENCY_NOT_IN_COURSE}.</li>
 *   <li>{@code reports} (Sprint 9) aggregates session metrics by
 *       competency / capacity for boletas.</li>
 * </ul>
 */
package com.edushift.modules.academic.competency;
