package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the "Mis sesiones hoy" widget
 * (Sprint 9B / BE-9B.1).
 *
 * <p>Represents one of the bearer teacher's sessions that
 * requires attention: either pending to be opened, or currently
 * open. Ordered by {@code sectionName asc, slot asc} so the FE
 * can render a stable list.</p>
 *
 * @param sessionPublicUuid public UUID of the session (null when
 *                          there is no session for this section/slot
 *                          today and the teacher is expected to
 *                          open one).
 * @param sectionPublicUuid public UUID of the section.
 * @param sectionName       section name.
 * @param occurredOn         session date.
 * @param slot               time-of-day slot.
 * @param status             session status (typically {@code PENDING}
 *                          when no session exists yet, or
 *                          {@code ACTIVE} when one is open).
 * @param enrolledStudents   number of active enrollments in the
 *                          section (for the "30/30 confirmed" pill
 *                          in the FE).
 * @param presentCount       number of records PRESENT in the session
 *                          (0 if no session yet).
 * @param absentCount        number of records ABSENT in the session
 *                          (0 if no session yet).
 */
public record MySessionItem(
		UUID sessionPublicUuid,
		UUID sectionPublicUuid,
		String sectionName,
		LocalDate occurredOn,
		AttendanceSessionSlot slot,
		String status,
		long enrolledStudents,
		long presentCount,
		long absentCount,
		Instant closedAt
) {
}
