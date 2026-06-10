package com.edushift.modules.attendance.entity;

/**
 * Time-of-day slot for an {@link AttendanceSession}.
 *
 * <p>Sprint 6 / BE-6.1. Persisted as {@code varchar(16)} with a CHECK
 * constraint at the DB level (see {@code V30__create_attendance_tables.sql}).
 *
 * <ul>
 *   <li>{@code MORNING} - shifts that start in the morning.
 *   <li>{@code AFTERNOON} - shifts that start in the afternoon.
 *   <li>{@code FULL_DAY} - default for tenants without dual shift.
 * </ul>
 *
 * <p>Two ACTIVE sessions cannot coexist for the same
 * {@code (section, day, slot)} triple — see
 * {@code uk_attendance_sessions_section_day_slot_active}.
 */
public enum AttendanceSessionSlot {

	MORNING,
	AFTERNOON,
	FULL_DAY
}
