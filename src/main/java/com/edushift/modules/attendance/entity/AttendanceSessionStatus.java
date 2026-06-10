package com.edushift.modules.attendance.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of an {@link AttendanceSession} (Sprint 6 / BE-6.1).
 *
 * <pre>
 *   ACTIVE ----close----&gt; CLOSED
 * </pre>
 *
 * <ul>
 *   <li>{@code ACTIVE} - the teacher can scan students; check-ins create
 *       {@link AttendanceRecord}s with {@code PRESENT}/{@code LATE}.
 *       The session enforces idempotency by {@code (session, student)}.
 *   <li>{@code CLOSED} - terminal. The service materializes
 *       {@code ABSENT} records for non-scanned enrolled students at
 *       transition time (ADR-6.6). Further check-ins return
 *       {@code 409 SESSION_CLOSED}. Manual edits via
 *       {@code PUT /records/{id}} remain allowed within a 24h window
 *       for {@code TEACHER}, always for {@code TENANT_ADMIN}
 *       (ADR-6.7).
 * </ul>
 */
public enum AttendanceSessionStatus {

	ACTIVE,
	CLOSED;

	public boolean isTerminal() {
		return this == CLOSED;
	}

	/**
	 * @return legal "next" states from the current one. Empty when
	 *         the current state is terminal.
	 */
	public Set<AttendanceSessionStatus> legalNext() {
		return switch (this) {
			case ACTIVE -> EnumSet.of(CLOSED);
			case CLOSED -> EnumSet.noneOf(AttendanceSessionStatus.class);
		};
	}
}
