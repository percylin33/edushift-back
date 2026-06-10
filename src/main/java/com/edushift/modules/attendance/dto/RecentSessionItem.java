package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the "Ultimas sesiones cerradas" widget
 * (Sprint 6 / BE-6.5).
 *
 * <p>Carries everything the FE needs to render a card
 * ("5to A - Manana - 3 PRESENT, 1 LATE, 1 ABSENT, cerrado a las
 * 12:30") without a second round-trip.
 *
 * <h3>Counters semantics</h3>
 * The four {@code *Count} fields are computed by the same
 * {@code countByStatus} queries the close endpoint uses, so the
 * dashboard numbers can never drift from the session detail.
 *
 * @param sessionPublicUuid  public UUID of the session.
 * @param sectionPublicUuid  public UUID of the section the session
 *                           belongs to.
 * @param sectionName        section name (denormalised to avoid
 *                           an extra lookup in the FE).
 * @param occurredOn         fecha local de la sesion.
 * @param slot               time-of-day slot.
 * @param closedAt           timestamp when the session transitioned
 *                           to CLOSED.
 * @param presentCount       number of records PRESENT.
 * @param lateCount          number of records LATE.
 * @param absentCount        number of records ABSENT (includes
 *                           materialised ones, ADR-6.6).
 * @param excusedCount       number of records EXCUSED.
 * @param totalRecords       {@code present + late + absent + excused}.
 *                           Convenience field for the FE card
 *                           "12 / 30".
 */
public record RecentSessionItem(
		UUID sessionPublicUuid,
		UUID sectionPublicUuid,
		String sectionName,
		LocalDate occurredOn,
		AttendanceSessionSlot slot,
		Instant closedAt,
		long presentCount,
		long lateCount,
		long absentCount,
		long excusedCount,
		long totalRecords
) {
}
