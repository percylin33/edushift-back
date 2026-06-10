package com.edushift.modules.evaluations.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of an {@link com.edushift.modules.evaluations.entity.Evaluation}
 * (Sprint 5B / BE-5B.1, ADR-5B.7).
 *
 * <pre>
 *   DRAFT ----publish----&gt; PUBLISHED ----close----&gt; CLOSED
 * </pre>
 *
 * <ul>
 *   <li>{@code DRAFT} — fully editable: name, weight, due_date, kind,
 *       scale, anchor. No {@code GradeRecord} may exist yet
 *       (BE-5B.3 enforces {@code GRADE_EVAL_NOT_PUBLISHED}).</li>
 *   <li>{@code PUBLISHED} — visible to students (FE-5B.1). The
 *       {@code scheduled_date} is frozen. Editing is restricted to
 *       {@code description} and {@code due_date} only (the FE shows
 *       a "close to edit more" banner).</li>
 *   <li>{@code CLOSED} — terminal, read-only. No more writes, no
 *       edits, no delete. The grade book uses this as the closing
 *       boundary for the period.</li>
 * </ul>
 */
public enum EvaluationStatus {

	DRAFT,
	PUBLISHED,
	CLOSED;

	/**
	 * Terminal states are final: the row is effectively read-only from
	 * here on. The service uses this to short-circuit state-transition
	 * attempts.
	 */
	public boolean isTerminal() {
		return this == CLOSED;
	}

	/**
	 * @return the legal "next" status set for the current state.
	 *         Empty if the current state is terminal.
	 */
	public Set<EvaluationStatus> legalNext() {
		return switch (this) {
			case DRAFT     -> EnumSet.of(PUBLISHED);
			case PUBLISHED -> EnumSet.of(CLOSED);
			case CLOSED    -> EnumSet.noneOf(EvaluationStatus.class);
		};
	}
}
