package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.attendance.dto.DashboardOverviewResponse;
import com.edushift.modules.attendance.dto.RecentSessionItem;
import com.edushift.modules.attendance.dto.TopAbsentSectionItem;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.service.DashboardService;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link DashboardService} (Sprint 6 / BE-6.5).
 *
 * <p>Composes 5 read-only aggregate queries against the caller's
 * tenant:
 * <ol>
 *   <li><strong>Today overview</strong> ({@link #loadTodayOverview(Instant, LocalDate, UUID)}):
 *       4 KPIs, all from indexes covering
 *       {@code (tenant_id, status, occurred_at)} and
 *       {@code (tenant_id, status, occurred_on)}.</li>
 *   <li><strong>Top absent sections</strong> ({@link #loadTopAbsentSections(UUID, Instant, int)}):
 *       SQL {@code GROUP BY} on
 *       {@code attendance_records.tenant_id, session.section_id} with
 *       7-day window.</li>
 *   <li><strong>Recent closed sessions</strong> ({@link #loadRecentClosedSessions(UUID, int)}):
 *       4 {@code countByStatus} calls per session, the same shape the
 *       close endpoint already uses.</li>
 * </ol>
 *
 * <h3>Why {@code NamedParameterJdbcTemplate}?</h3>
 * <ul>
 *   <li>The top-absent query is hand-written and benefits from named
 *       parameters + readable SQL over the JPQL alternative (no
 *       constructor expression, no Tuple hydration edge cases).</li>
 *   <li>Native SQL gives us {@code date_trunc('day', now())} and
 *       {@code to_char()} control for the timezone decision in
 *       ADR-6.12 — JPQL would have needed a derived function
 *       registered with {@code Hibernate.STRING}.</li>
 *   <li>The 4 today KPIs are JPQL — the indexes already cover
 *       them, so there's no performance win in rewriting.</li>
 * </ul>
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

	private static final int DEFAULT_TOP_ABSENT_LIMIT = 5;
	private static final int DEFAULT_RECENT_CLOSED_LIMIT = 5;
	private static final int TOP_ABSENT_WINDOW_DAYS = 7;

	private final CurrentUserProvider currentUserProvider;
	private final AttendanceSessionRepository sessionRepository;
	private final AttendanceRecordRepository recordRepository;
	private final NamedParameterJdbcTemplate jdbc;

	public DashboardServiceImpl(
			CurrentUserProvider currentUserProvider,
			AttendanceSessionRepository sessionRepository,
			AttendanceRecordRepository recordRepository,
			DataSource dataSource) {
		this.currentUserProvider = currentUserProvider;
		this.sessionRepository = sessionRepository;
		this.recordRepository = recordRepository;
		this.jdbc = new NamedParameterJdbcTemplate(dataSource);
	}

	// =====================================================================
	// Public API
	// =====================================================================

	@Override
	@Transactional(readOnly = true)
	public DashboardOverviewResponse getOverview() {
		UUID tenantId = currentUserProvider.currentTenantId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated tenant is required for dashboard"));
		Instant now = Instant.now();
		LocalDate todayUtc = now.atZone(ZoneOffset.UTC).toLocalDate();
		Instant dayStart = todayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
		Instant dayEnd = dayStart.plus(java.time.Duration.ofDays(1));

		TodayOverview today = loadTodayOverview(now, dayStart, dayEnd, tenantId, todayUtc);
		List<TopAbsentSectionItem> topAbsent =
				loadTopAbsentSections(tenantId, now, DEFAULT_TOP_ABSENT_LIMIT);
		List<RecentSessionItem> recent =
				loadRecentClosedSessions(tenantId, DEFAULT_RECENT_CLOSED_LIMIT);

		return new DashboardOverviewResponse(
				now,
				today.rate,
				today.enrollmentsConsidered,
				today.openSessions,
				today.uniqueStudents,
				today.totalAbsences,
				topAbsent,
				recent);
	}

	// =====================================================================
	// Today overview (4 KPIs)
	// =====================================================================

	/** Mutable carrier for the four today KPIs. */
	private record TodayOverview(
			double rate,
			long enrollmentsConsidered,
			long openSessions,
			long uniqueStudents,
			long totalAbsences) {
		static TodayOverview zero() {
			return new TodayOverview(DashboardOverviewResponse.ZERO_RATE,
					0L, 0L, 0L, 0L);
		}
	}

	/**
	 * Bundle the 4 KPI queries in a single, easy-to-read block.
	 *
	 * @param dayStart  inclusive lower bound of the today window
	 *                  (UTC 00:00:00).
	 * @param dayEnd    exclusive upper bound (UTC next-day 00:00:00).
	 * @param todayUtc  the calendar date used by
	 *                  {@code countActiveOn(todayUtc)} (KPI #2).
	 */
	private TodayOverview loadTodayOverview(
			Instant now,
			Instant dayStart,
			Instant dayEnd,
			UUID tenantId,
			LocalDate todayUtc) {

		// KPI #2: ACTIVE sessions on today (independent of the
		// records; uses the dedicated index
		// idx_attendance_sessions_tenant_status_date).
		long openSessions = sessionRepository.countActiveOn(todayUtc);

		// KPI #1 denominator: ACTIVE enrollments in sections that
		// have at least one session today. Inner join via
		// DISTINCT student_id in the SQL keeps it down to one
		// round-trip. The query is a sub-select to avoid double-
		// counting a student who has more than one ACTIVE
		// enrollment (rare but legal during a transfer window).
		Long enrollments = jdbc.queryForObject(
				"""
				select count(distinct e.student_id)
				from edushift.student_enrollments e
				join edushift.attendance_sessions s
				  on s.tenant_id = e.tenant_id
				 and s.section_id = e.section_id
				 and s.occurred_on = :today
				where e.tenant_id = :tenantId
				  and e.status = 'ACTIVE'
				  and s.deleted = false
				  and e.deleted = false
				""",
				new MapSqlParameterSource()
						.addValue("tenantId", tenantId)
						.addValue("today", java.sql.Date.valueOf(todayUtc)),
				Long.class);
		long enrollmentsConsidered = enrollments == null ? 0L : enrollments;

		// Numerator: any record with status PRESENT or LATE
		// whose occurred_at falls in the UTC today window. Uses
		// idx_attendance_records_tenant_status_date.
		Long presentLike = jdbc.queryForObject(
				"""
				select count(*) from edushift.attendance_records r
				where r.tenant_id = :tenantId
				  and r.status in ('PRESENT','LATE')
				  and r.occurred_at >= :dayStart
				  and r.occurred_at <  :dayEnd
				  and r.deleted = false
				""",
				new MapSqlParameterSource()
						.addValue("tenantId", tenantId)
						.addValue("dayStart", Timestamp.from(dayStart))
						.addValue("dayEnd", Timestamp.from(dayEnd)),
				Long.class);
		long presentLate = presentLike == null ? 0L : presentLike;

		double rate = enrollmentsConsidered == 0
				? DashboardOverviewResponse.ZERO_RATE
				: round2((presentLate * 100.0d) / enrollmentsConsidered);

		// KPI #3: distinct students with any record today.
		Long uniqueStudents = jdbc.queryForObject(
				"""
				select count(distinct r.student_id)
				from edushift.attendance_records r
				where r.tenant_id = :tenantId
				  and r.occurred_at >= :dayStart
				  and r.occurred_at <  :dayEnd
				  and r.deleted = false
				""",
				new MapSqlParameterSource()
						.addValue("tenantId", tenantId)
						.addValue("dayStart", Timestamp.from(dayStart))
						.addValue("dayEnd", Timestamp.from(dayEnd)),
				Long.class);
		long uniqueCount = uniqueStudents == null ? 0L : uniqueStudents;

		// KPI #4: total ABSENT today.
		long totalAbsences = recordRepository.countByStatusInRange(
				AttendanceRecordStatus.ABSENT, dayStart, dayEnd);

		log.debug("[dashboard] today overview -- tenant={} open={} rate={} "
						+ "enroll={} presentLate={} unique={} absent={}",
				tenantId, openSessions, rate, enrollmentsConsidered,
				presentLate, uniqueCount, totalAbsences);

		return new TodayOverview(rate, enrollmentsConsidered, openSessions,
				uniqueCount, totalAbsences);
	}

	// =====================================================================
	// Top absent sections (7d)
	// =====================================================================

	/**
	 * Top N sections with the most {@code ABSENT} records in the last
	 * 7 days.
	 *
	 * <p>The query is intentionally hand-written instead of a JPQL
	 * {@code GROUP BY} because the join to {@code grades} for the
	 * human-readable name trips up Hibernate's auto-derivation in
	 * older versions. SQL keeps the column projection explicit.
	 *
	 * <p>The "enrolled" count is computed in a second small query to
	 * keep the GROUP BY clean — a correlated sub-select would work
	 * but Postgres tends to materialise it less efficiently than a
	 * batched lookup. With {@code topN <= 5} the second round-trip is
	 * negligible.
	 */
	private List<TopAbsentSectionItem> loadTopAbsentSections(
			UUID tenantId, Instant now, int limit) {
		Instant windowStart = now.minus(java.time.Duration.ofDays(
				TOP_ABSENT_WINDOW_DAYS));

		// We need both: (1) ABSENT count and (2) the section's
		// display name + grade name. We also need the enrollment
		// count for the "% ausentismo" denominator the FE
		// computes. Three lookups, but the indexes cover each
		// independently.
		List<Map<String, Object>> rows = jdbc.queryForList(
				"""
				select
					sec.public_uuid   as section_uuid,
					sec.name          as section_name,
					g.name            as grade_name,
					sec.id            as section_id,
					count(r.id)       as absent_count
				from edushift.attendance_records r
				join edushift.attendance_sessions s
				  on s.id = r.session_id and s.tenant_id = r.tenant_id
				join edushift.sections sec
				  on sec.id = s.section_id and sec.tenant_id = s.tenant_id
				left join edushift.grades g
				  on g.id = sec.grade_id and g.tenant_id = sec.tenant_id
				where r.tenant_id = :tenantId
				  and r.status = 'ABSENT'
				  and r.occurred_at >= :windowStart
				  and r.occurred_at <  :now
				  and r.deleted = false
				  and s.deleted = false
				  and sec.deleted = false
				group by sec.id, sec.public_uuid, sec.name, g.name
				order by absent_count desc, sec.name asc
				limit :topN
				""",
				new MapSqlParameterSource()
						.addValue("tenantId", tenantId)
						.addValue("windowStart", Timestamp.from(windowStart))
						.addValue("now", Timestamp.from(now))
						.addValue("topN", limit));

		if (rows.isEmpty()) {
			return List.of();
		}

		// Pre-load enrollment counts in one query to avoid the
		// N+1 on the FE side (and on us).
		// NOTE: sections.id is UUID (see V15__create_sections_table.sql),
		// not bigint, so we keep the in-memory key as UUID throughout.
		List<UUID> sectionIds = rows.stream()
				.map(r -> (UUID) r.get("section_id"))
				.toList();
		Map<UUID, Long> enrolledBySection = loadEnrolledBySection(
				tenantId, sectionIds);

		List<TopAbsentSectionItem> out = new ArrayList<>(rows.size());
		for (Map<String, Object> r : rows) {
			UUID sectionId = (UUID) r.get("section_id");
			out.add(new TopAbsentSectionItem(
					(UUID) r.get("section_uuid"),
					(String) r.get("section_name"),
					(String) r.get("grade_name"),
					((Number) r.get("absent_count")).longValue(),
					enrolledBySection.getOrDefault(sectionId, 0L)));
		}
		return out;
	}

	private Map<UUID, Long> loadEnrolledBySection(UUID tenantId, List<UUID> sectionIds) {
		if (sectionIds.isEmpty()) return Map.of();
		List<Map<String, Object>> rows = jdbc.queryForList(
				"""
				select section_id as section_id,
				       count(*)    as enrolled
				from edushift.student_enrollments
				where tenant_id = :tenantId
				  and status = 'ACTIVE'
				  and section_id in (:sectionIds)
				  and deleted = false
				group by section_id
				""",
				new MapSqlParameterSource()
						.addValue("tenantId", tenantId)
						.addValue("sectionIds", sectionIds));
		Map<UUID, Long> out = new LinkedHashMap<>();
		for (Map<String, Object> r : rows) {
			UUID sectionId = (UUID) r.get("section_id");
			long enrolled = ((Number) r.get("enrolled")).longValue();
			out.put(sectionId, enrolled);
		}
		return out;
	}

	// =====================================================================
	// Recent closed sessions
	// =====================================================================

	/**
	 * Hydrates the last N closed sessions plus their 4 status
	 * counters. Uses {@code AttendanceRecordRepository} for the
	 * counters so the SQL stays in one place per domain — the
	 * dashboard benefits from the same indexing as the
	 * "session detail" view.
	 */
	private List<RecentSessionItem> loadRecentClosedSessions(UUID tenantId, int limit) {
		List<AttendanceSession> recent = sessionRepository.findRecentClosed(
				org.springframework.data.domain.PageRequest.of(0, limit));
		if (recent.isEmpty()) return List.of();

		List<RecentSessionItem> out = new ArrayList<>(recent.size());
		for (AttendanceSession session : recent) {
			// Belt-and-suspenders: TenantAware should already filter
			// this, but if a future repository change drops the
			// filter we want the dashboard to fail closed (skip
			// the row) instead of leaking.
			if (!tenantId.equals(session.getTenantId())) {
				log.warn("[dashboard] skipping non-tenant session in recent "
						+ "closed -- session={} expectedTenant={} actualTenant={}",
						session.getPublicUuid(), tenantId,
						session.getTenantId());
				continue;
			}
			Section section = session.getSection();
			long present = recordRepository.countBySessionAndStatus(
					session, AttendanceRecordStatus.PRESENT);
			long late = recordRepository.countBySessionAndStatus(
					session, AttendanceRecordStatus.LATE);
			long absent = recordRepository.countBySessionAndStatus(
					session, AttendanceRecordStatus.ABSENT);
			long excused = recordRepository.countBySessionAndStatus(
					session, AttendanceRecordStatus.EXCUSED);
			long total = present + late + absent + excused;

			// The lazy `Section` access here would normally hit
			// the L1 cache thanks to Hibernate session reuse in
			// the same transaction. If it ever stops being the
			// case, the alternative is to load the section name
			// in the same SQL block — but we'd lose the JPQL
			// readability of the existing `findRecentClosed`.
			String sectionName = section.getName();
			AttendanceSessionSlot slot = session.getSlot();
			UUID sectionUuid = section.getPublicUuid();
			UUID sessionUuid = session.getPublicUuid();
			LocalDate occurredOn = session.getOccurredOn();
			Instant closedAt = session.getClosedAt();

			out.add(new RecentSessionItem(
					sessionUuid,
					sectionUuid,
					sectionName,
					occurredOn,
					slot,
					closedAt,
					present,
					late,
					absent,
					excused,
					total));
		}
		return out;
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static double round2(double value) {
		return Math.round(value * 100.0d) / 100.0d;
	}
}
