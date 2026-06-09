package com.edushift.modules.sessions.learning.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle states of a {@link LearningSession} (Sprint 5A / BE-5A.4).
 *
 * <p>Allowed transitions:</p>
 * <pre>{@code
 *   PLANNED      -> IN_PROGRESS, CANCELLED
 *   IN_PROGRESS  -> COMPLETED, CANCELLED
 *   COMPLETED    -> (terminal)
 *   CANCELLED    -> (terminal)
 * }</pre>
 *
 * <p>Both Postgres CHECK constraints and the service layer enforce the
 * timestamps coherence: {@code started_at} only with
 * {@code IN_PROGRESS+}, {@code ended_at} only with {@code COMPLETED},
 * and {@code cancelled_at} only with {@code CANCELLED}.</p>
 */
public enum SessionStatus {

	PLANNED,
	IN_PROGRESS,
	COMPLETED,
	CANCELLED;

	private static final Set<SessionStatus> TERMINAL_STATES =
			EnumSet.of(COMPLETED, CANCELLED);

	public boolean isTerminal() {
		return TERMINAL_STATES.contains(this);
	}

	public boolean canTransitionTo(SessionStatus target) {
		if (target == null || target == this || isTerminal()) {
			return false;
		}
		return switch (this) {
			case PLANNED      -> target == IN_PROGRESS || target == CANCELLED;
			case IN_PROGRESS  -> target == COMPLETED  || target == CANCELLED;
			default           -> false;
		};
	}
}
