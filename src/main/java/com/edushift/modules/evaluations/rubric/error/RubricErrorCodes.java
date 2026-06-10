package com.edushift.modules.evaluations.rubric.error;

/**
 * Stable error codes for the {@code evaluations.rubric} sub-module
 * (Sprint 5B / BE-5B.2).
 *
 * <p>Codes are part of the public API contract — never rename. The full
 * error contract (HTTP status, recovery hints) is documented on
 * {@link com.edushift.modules.evaluations.rubric.service.RubricService}
 * and mirrored in {@code docs/product/sprints/sprint-05b-evaluations-rubrics.md}.</p>
 *
 * <h3>Grouping</h3>
 * <ul>
 *   <li>{@code RUB_NAME_EXISTS} — uniqueness conflict on
 *       {@code (tenant, lower(name))} (409).</li>
 *   <li>{@code RUB_SYSTEM_READ_ONLY} — try to mutate a system
 *       (MINEDU-seed) rubric (403). The only valid operation on a
 *       system rubric is {@code POST /rubrics/{uuid}/fork}.</li>
 *   <li>{@code RUB_CRITERIA_WEIGHT_SUM} — sum of criterion weights
 *       is not 100.0 (400).</li>
 *   <li>{@code RUB_CRITERIA_COUNT} — number of criteria is not in
 *       1..10 (400).</li>
 *   <li>{@code RUB_LEVELS_COUNT} — number of levels is not in 2..4 (400).</li>
 *   <li>{@code RUB_LEVEL_CODE_DUPLICATE} — two levels share the same
 *       code (400).</li>
 *   <li>{@code RUB_LEVEL_UNKNOWN} — a criterion descriptor references
 *       a level code that is not in the rubric's {@code levels[]}
 *       array (400).</li>
 *   <li>{@code RUB_CRITERION_KEY_DUPLICATE} — two criteria share the
 *       same key (400).</li>
 *   <li>{@code RUB_DESCRIPTOR_DUPLICATE} — two descriptors on the same
 *       criterion target the same level (400).</li>
 *   <li>{@code RUB_CANNOT_FORK_NON_SYSTEM} — fork attempted on a
 *       tenant-owned (non-system) rubric (400).</li>
 *   <li>{@code RUB_PARENT_NOT_FOUND} — {@code parentRubricId}
 *       references a non-existent or soft-deleted rubric (404).</li>
 * </ul>
 */
public final class RubricErrorCodes {

	/** 409 — another rubric in the same tenant already uses {@code name} (case-insensitive). */
	public static final String RUB_NAME_EXISTS = "RUB_NAME_EXISTS";

	/** 403 — system rubrics are read-only. Fork them to make changes. */
	public static final String RUB_SYSTEM_READ_ONLY = "RUB_SYSTEM_READ_ONLY";

	/** 400 — sum of criterion weights is not exactly 100.0. */
	public static final String RUB_CRITERIA_WEIGHT_SUM = "RUB_CRITERIA_WEIGHT_SUM";

	/** 400 — number of criteria is not in 1..10. */
	public static final String RUB_CRITERIA_COUNT = "RUB_CRITERIA_COUNT";

	/** 400 — number of levels is not in 2..4. */
	public static final String RUB_LEVELS_COUNT = "RUB_LEVELS_COUNT";

	/** 400 — two levels share the same {@code code}. */
	public static final String RUB_LEVEL_CODE_DUPLICATE = "RUB_LEVEL_CODE_DUPLICATE";

	/** 400 — a descriptor references a level code that is not defined. */
	public static final String RUB_LEVEL_UNKNOWN = "RUB_LEVEL_UNKNOWN";

	/** 400 — two criteria share the same {@code key}. */
	public static final String RUB_CRITERION_KEY_DUPLICATE = "RUB_CRITERION_KEY_DUPLICATE";

	/** 400 — two descriptors on the same criterion target the same level. */
	public static final String RUB_DESCRIPTOR_DUPLICATE = "RUB_DESCRIPTOR_DUPLICATE";

	/** 400 — fork attempted on a non-system rubric. */
	public static final String RUB_CANNOT_FORK_NON_SYSTEM = "RUB_CANNOT_FORK_NON_SYSTEM";

	/** 404 — {@code parentRubricId} references a non-existent rubric. */
	public static final String RUB_PARENT_NOT_FOUND = "RUB_PARENT_NOT_FOUND";

	private RubricErrorCodes() {
	}
}
