package com.edushift.modules.attendance.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Outcome stored in an {@link AttendanceRecord} for a (session, student)
 * pair (Sprint 6 / BE-6.1).
 *
 * <ul>
 *   <li>{@code PRESENT} - scanned within the on-time window
 *       (i.e. {@code occurredAt <= session.startsAt + lateAfterMinutes}).
 *       Requires a non-null {@code scanned_by_user_id}.
 *   <li>{@code LATE} - scanned after the on-time window. Same shape as
 *       {@code PRESENT} (FK to scanner) but flagged differently for
 *       reports / dashboards.
 *   <li>{@code ABSENT} - virtual while the session is {@code ACTIVE};
 *       materialized by the service when the session transitions to
 *       {@code CLOSED} for any enrolled student without a record.
 *   <li>{@code EXCUSED} - manually set via
 *       {@code PUT /records/{id}} (e.g. medical appointment); does
 *       not require {@code scanned_by_user_id}.
 * </ul>
 */
public enum AttendanceRecordStatus {

	PRESENT,
	LATE,
	ABSENT,
	EXCUSED;

	/**
	 * @return {@code true} when the status implies the student was
	 *         physically scanned (PRESENT or LATE). Used by the DB
	 *         CHECK constraint mirror in the service to assert
	 *         {@code scanned_by_user_id != null} before persist.
	 */
	public boolean requiresScanner() {
		return this == PRESENT || this == LATE;
	}

	/**
	 * Legal manual transitions via {@code PUT /records/{id}}. Any other
	 * combination returns 400 {@code VALIDATION_ERROR}.
	 *
	 * <p>Note: {@code TENANT_ADMIN} can transition out of {@code ABSENT}
	 * to {@code PRESENT} or {@code EXCUSED} (justified absence); a
	 * {@code TEACHER} would only do {@code PRESENT/LATE -> EXCUSED}
	 * (correction) within the 24h window.
	 */
	public Set<AttendanceRecordStatus> legalManualTransitions() {
		return switch (this) {
			case PRESENT -> EnumSet.of(LATE, ABSENT, EXCUSED);
			case LATE    -> EnumSet.of(PRESENT, ABSENT, EXCUSED);
			case ABSENT  -> EnumSet.of(PRESENT, EXCUSED);
			case EXCUSED -> EnumSet.of(PRESENT, ABSENT);
		};
	}
}
