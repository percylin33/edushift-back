package com.edushift.modules.attendance.dto;

import java.time.Instant;
import java.util.List;

/**
 * Top-level response of the admin attendance dashboard
 * (Sprint 6 / BE-6.5).
 *
 * <p>Composed of three logical blocks the FE renders as separate
 * widgets (see FE-6.4 / D-ATT §9.5):
 * <ol>
 *   <li>Today overview (4 KPIs): {@link #attendanceRateToday},
 *       {@link #openSessions}, {@link #uniqueStudentsRegisteredToday},
 *       {@link #totalAbsencesToday}.</li>
 *   <li>{@link #topAbsentSections} — "Top secciones con mas
 *       inasistencias" (ultimos 7 dias, max 5 items).</li>
 *   <li>{@link #recentClosedSessions} — "Ultimas sesiones cerradas"
 *       (max 5 items).</li>
 * </ol>
 *
 * <h3>KPI semantics</h3>
 * All numbers are computed in UTC {@code today}. The tenant's
 * timezone alignment is left as ADR-6.12 (tracked in
 * {@code docs/product/tech-debt.md}).
 *
 * @param generatedAt                  timestamp the snapshot was
 *                                     computed at. Useful for the FE
 *                                     to show "Actualizado hace 3s"
 *                                     and to debug cache-vs-fresh
 *                                     questions later.
 * @param attendanceRateToday          percentage in the
 *                                     {@code [0.00, 100.00]} range.
 *                                     {@code 0.00} when there are
 *                                     neither enrollments nor
 *                                     sessions today (no
 *                                     division-by-zero crashes).
 * @param enrollmentsConsidered        number of active enrollments
 *                                     in sections that have a
 *                                     session on {@code today} — the
 *                                     denominator of
 *                                     {@code attendanceRateToday}.
 *                                     Zero means "no class today" and
 *                                     the FE should render the "—%"
 *                                     placeholder.
 * @param openSessions                 number of {@code ACTIVE}
 *                                     sessions on {@code today}.
 * @param uniqueStudentsRegisteredToday distinct {@code student_id}s
 *                                     in any record with
 *                                     {@code occurred_at} on
 *                                     {@code today}.
 * @param totalAbsencesToday           number of records with
 *                                     {@code status=ABSENT} and
 *                                     {@code occurred_at} on
 *                                     {@code today}.
 * @param topAbsentSections            desc-sorted by
 *                                     {@code absentCount}.
 * @param recentClosedSessions         desc-sorted by
 *                                     {@code closedAt}.
 */
public record DashboardOverviewResponse(
		Instant generatedAt,
		double attendanceRateToday,
		long enrollmentsConsidered,
		long openSessions,
		long uniqueStudentsRegisteredToday,
		long totalAbsencesToday,
		List<TopAbsentSectionItem> topAbsentSections,
		List<RecentSessionItem> recentClosedSessions
) {

	/** Defensive constant the FE can rely on for "no class today". */
	public static final double ZERO_RATE = 0.0d;

	/**
	 * Convenience for empty snapshots. Keeps the controller code
	 * free of {@code new ArrayList<>(0)} clutter when there is no
	 * class in the tenant yet.
	 */
	public static DashboardOverviewResponse empty() {
		return new DashboardOverviewResponse(
				Instant.now(),
				ZERO_RATE,
				0L,
				0L,
				0L,
				0L,
				List.of(),
				List.of());
	}
}
