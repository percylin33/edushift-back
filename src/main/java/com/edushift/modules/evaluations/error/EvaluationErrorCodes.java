package com.edushift.modules.evaluations.error;

/**
 * Stable error codes for the {@code evaluations} sub-module (Sprint 5B / BE-5B.1).
 *
 * <p>Codes are part of the public API contract — never rename. The full
 * error contract (HTTP status, recovery hints) is documented on
 * {@link com.edushift.modules.evaluations.service.EvaluationService}
 * and mirrored in {@code docs/product/sprints/sprint-05b-evaluations-rubrics.md}.</p>
 *
 * <h3>Grouping</h3>
 * <ul>
 *   <li>{@code EVAL_NAME_EXISTS} — uniqueness conflict (409).</li>
 *   <li>{@code EVAL_DATE_INVERTED} — date window violation (400).</li>
 *   <li>{@code EVAL_KIND_SCALE_MISMATCH} — incoherent kind/scale pair (400).</li>
 *   <li>{@code EVAL_NOT_EDITABLE} — try to edit fields frozen in PUBLISHED (409).</li>
 *   <li>{@code EVAL_CLOSED} — try to write against a CLOSED evaluation (409).</li>
 *   <li>{@code EVAL_NOT_IN_ASSIGNMENT} — anchor FK points outside the assignment scope (400).</li>
 *   <li>{@code EVAL_UNIT_NOT_IN_COURSE} — anchor unit belongs to another course (400).</li>
 *   <li>{@code EVAL_SESSION_NOT_IN_ASSIGNMENT} — anchor session belongs to another assignment (400).</li>
 *   <li>{@code EVAL_HAS_GRADES} — delete attempted while {@code GradeRecord}s exist (409, BE-5B.3).</li>
 *   <li>{@code EVAL_ILLEGAL_TRANSITION} — lifecycle jump that the state machine forbids (400).</li>
 * </ul>
 */
public final class EvaluationErrorCodes {

	/** 409 — another evaluation in the same assignment already uses {@code name} (case-insensitive). */
	public static final String EVAL_NAME_EXISTS = "EVAL_NAME_EXISTS";

	/** 400 — {@code dueDate < scheduledDate}. */
	public static final String EVAL_DATE_INVERTED = "EVAL_DATE_INVERTED";

	/** 400 — kind/scale combination is not in the allowed matrix. */
	public static final String EVAL_KIND_SCALE_MISMATCH = "EVAL_KIND_SCALE_MISMATCH";

	/** 409 — fields not in the PUBLISHED-editable set were patched. */
	public static final String EVAL_NOT_EDITABLE = "EVAL_NOT_EDITABLE";

	/** 409 — the evaluation is in CLOSED state; no writes allowed. */
	public static final String EVAL_CLOSED = "EVAL_CLOSED";

	/** 400 — the anchor (unit/session) does not belong to the assignment's scope. */
	public static final String EVAL_NOT_IN_ASSIGNMENT = "EVAL_NOT_IN_ASSIGNMENT";

	/** 400 — {@code unitPublicUuid} references a unit of another course. */
	public static final String EVAL_UNIT_NOT_IN_COURSE = "EVAL_UNIT_NOT_IN_COURSE";

	/** 400 — {@code learningSessionPublicUuid} references a session of another assignment. */
	public static final String EVAL_SESSION_NOT_IN_ASSIGNMENT = "EVAL_SESSION_NOT_IN_ASSIGNMENT";

	/** 409 — at least one {@code GradeRecord} references this evaluation. BE-5B.3 will throw this. */
	public static final String EVAL_HAS_GRADES = "EVAL_HAS_GRADES";

	/** 400 — lifecycle transition is not in {@code EvaluationStatus.legalNext()}. */
	public static final String EVAL_ILLEGAL_TRANSITION = "EVAL_ILLEGAL_TRANSITION";

	private EvaluationErrorCodes() {
	}
}
