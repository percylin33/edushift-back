package com.edushift.modules.attendance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Top-level response of the teacher attendance dashboard
 * (Sprint 9B / BE-9B.1).
 *
 * <p>The shape mirrors {@link DashboardOverviewResponse} but is
 * <strong>scoped to the bearer teacher's own assignments</strong>:
 * a {@code TEACHER} caller never sees another teacher's
 * sessions/absences/rankings. Cross-teacher isolation is enforced at
 * the SQL layer (every query is anchored to the teacher's active
 * assignments) and verified end-to-end by
 * {@code TeacherDashboardIT}.</p>
 *
 * <h3>Blocks</h3>
 * <ol>
 *   <li>Today overview (4 KPIs): {@link #attendanceRateToday},
 *       {@link #openSessions}, {@link #uniqueStudentsRegisteredToday},
 *       {@link #totalAbsencesToday}.</li>
 *   <li>{@link #topAbsentSections} — top secciones del teacher
 *       (ultimos 7 dias, max 5 items).</li>
 *   <li>{@link #recentClosedSessions} — ultimas sesiones del teacher
 *       (max 5 items).</li>
 *   <li>{@link #mySectionsToday} — sesiones del teacher que
 *       necesitan su atencion hoy (sin abrir o abiertas).</li>
 * </ol>
 *
 * <h3>Empty case</h3>
 * The teacher may have zero active assignments (substitute teacher,
 * mid-quarter break, etc.). In that case the response is a clean
 * zeros snapshot with empty lists, so the FE can render "Sin
 * asignaciones activas" instead of "no data".
 */
public record TeacherDashboardResponse(
		Instant generatedAt,
		double attendanceRateToday,
		long enrollmentsConsidered,
		long openSessions,
		long uniqueStudentsRegisteredToday,
		long totalAbsencesToday,
		List<TopAbsentSectionItem> topAbsentSections,
		List<RecentSessionItem> recentClosedSessions,
		List<MySessionItem> mySectionsToday
) {

	/**
	 * Convenience for "teacher has no active assignments yet".
	 */
	public static TeacherDashboardResponse empty() {
		return new TeacherDashboardResponse(
				Instant.now(),
				DashboardOverviewResponse.ZERO_RATE,
				0L, 0L, 0L, 0L,
				List.of(), List.of(), List.of());
	}
}
