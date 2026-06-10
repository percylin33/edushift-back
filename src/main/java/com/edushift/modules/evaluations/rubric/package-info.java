/**
 * <strong>evaluations.rubric sub-module</strong> — scoring templates
 * used by {@code Evaluation}s of {@code kind=RUBRIC}. Sprint 5B /
 * BE-5B.2.
 *
 * <p>A {@code Rubric} is a per-tenant object that owns:
 * <ul>
 *   <li>A JSONB list of weighted {@code criteria} (1..10 items, sum of
 *       weights = 100.0). Each criterion carries a name, description and
 *       a per-level descriptor map.</li>
 *   <li>A JSONB list of achievement {@code levels} (2..4 items, default
 *       MINEDU set: EN_INICIO, EN_PROCESO, ESPERADO, SOBRESALIENTE).</li>
 *   <li>Forks: a per-tenant rubric can point to a {@code parentRubric}
 *       (which must be {@code isSystem = true}), creating a custom
 *       version that the tenant can edit freely.</li>
 * </ul></p>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (tenant, lower(name))} is unique on non-deleted rows
 *       (case-insensitive). System + per-tenant share the same index.</li>
 *   <li>{@code is_system = true} rows are read-only in the service layer
 *       ({@code RUB_SYSTEM_READ_ONLY}, 403). Forks must be created via
 *       {@code POST /rubrics/{uuid}/fork}.</li>
 *   <li>The sum of {@code criterion.weight} must be exactly 100.0
 *       ({@code RUB_CRITERIA_WEIGHT_SUM}, 400). Same for {@code level.code}
 *       uniqueness ({@code RUB_LEVEL_CODE_DUPLICATE}, 400).</li>
 *   <li>{@code level.code} must be one of the canonical MINEDU codes
 *       ({@code RubricLevel}) or any tenant-defined string, but the
 *       service enforces uniqueness within the array.</li>
 *   <li>Soft-delete is allowed; deleting a system rubric is rejected
 *       ({@code RUB_SYSTEM_READ_ONLY}).</li>
 * </ul>
 *
 * <h3>Downstream</h3>
 * <ul>
 *   <li>{@code Evaluation.kind = RUBRIC} references a {@code Rubric} via
 *       the M:N join {@code evaluation_rubrics} (BE-5B.4). Each evaluation
 *       has 0..1 rubric in MVP; constraint enforced in the join table.</li>
 *   <li>{@code GradeRecord} (BE-5B.3) snapshots the chosen {@code level.code}
 *       per criterion for historical accuracy when the rubric is later
 *       forked / edited.</li>
 * </ul>
 */
package com.edushift.modules.evaluations.rubric;
