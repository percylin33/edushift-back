package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.attendance.dto.DashboardOverviewResponse;
import com.edushift.modules.attendance.dto.MySessionItem;
import com.edushift.modules.attendance.dto.RecentSessionItem;
import com.edushift.modules.attendance.dto.TeacherDashboardResponse;
import com.edushift.modules.attendance.dto.TopAbsentSectionItem;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.service.TeacherDashboardService;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link TeacherDashboardService} (Sprint 9B / BE-9B.1).
 *
 * <p>Composes 4 read-only aggregate queries anchored to the bearer
 * teacher's own active assignments:</p>
 * <ol>
 *   <li><strong>Today overview</strong> (4 KPIs) — the same shape as
 *       the admin dashboard, but {@code section_id in (...)} filters
 *       to the teacher's sections only.</li>
 *   <li><strong>Top absent sections (7d)</strong> — {@code GROUP BY}
 *       on the teacher's section ids.</li>
 *   <li><strong>Recent closed sessions</strong> — 4 {@code countByStatus}
 *       per session, restricted to the teacher's assignments.</li>
 *   <li><strong>My sections today</strong> — sessions the teacher
 *       still needs to open (PENDING) or that are ACTIVE and waiting
 *       for close.</li>
 * </ol>
 *
 * <h3>Why {@code NamedParameterJdbcTemplate}?</h3>
 * Same reasoning as {@link DashboardServiceImpl}: hand-written SQL
 * with named parameters for readability, especially around
 * {@code section_id in (...)} anchoring that the JPA specs don't
 * model cleanly.
 *
 * <h3>Cross-tenant / cross-teacher</h3>
 * The teacher is resolved by {@code userId} (current authenticated
 * user's id). The {@code section_id in (...)} filter ensures
 * cross-teacher isolation even when two teachers share a section
 * (e.g. co-teachers in a multi-section course). The
 * {@code tenant_id = :tenant} filter on every query is the
 * ultimate backstop (the {@code @TenantId} Hibernate filter also
 * applies, but we add it explicitly because the SQL is hand-written).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherDashboardServiceImpl implements TeacherDashboardService {

	private static final int DEFAULT_TOP_ABSENT_LIMIT = 5;
	private static final int DEFAULT_RECENT_CLOSED_LIMIT = 5;
	private static final int MY_SECTIONS_TODAY_LIMIT = 10;
	private static final int TOP_ABSENT_WINDOW_DAYS = 7;

	private final CurrentUserProvider currentUserProvider;
	private final TeacherRepository teacherRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final AttendanceSessionRepository sessionRepository;
	private final AttendanceRecordRepository recordRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final DataSource dataSource;
	private NamedParameterJdbcTemplate jdbc;

	/** Spring builds {@link NamedParameterJdbcTemplate} via a
	 * BeanFactory post-processor so we don't need an explicit
	 * constructor — Lombok's {@code @RequiredArgsConstructor}
	 * handles the rest. The template itself is created lazily via
	 * {@link javax.sql.DataSource} so we have to set it up post-construct. */
	@jakarta.annotation.PostConstruct
	void initJdbc() {
		this.jdbc = new NamedParameterJdbcTemplate(dataSource);
	}

	// =====================================================================
	// Public API
	// =====================================================================

	@Override
	@Transactional(readOnly = true)
	public TeacherDashboardResponse getForCurrentTeacher() {
		UUID tenantId = currentUserProvider.currentTenantId()
				.orElseThrow(() -> new UnauthorizedException(
						"No tenant context for current user"));
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new UnauthorizedException(
						"No authenticated user for teacher dashboard"));
		Optional<Teacher> teacherOpt = teacherRepository.findByUserId(userId);
		if (teacherOpt.isEmpty()) {
			log.debug("[teacher-dashboard] user={} has no teacher link — returning empty",
					userId);
			return TeacherDashboardResponse.empty();
		}
		Teacher teacher = teacherOpt.get();
		List<UUID> sectionIds = teacherAssignmentRepository
				.findActiveSectionIdsByTeacher(teacher);
		if (sectionIds.isEmpty()) {
			log.debug("[teacher-dashboard] teacher={} has no active assignments — returning empty",
					teacher.getPublicUuid());
			return TeacherDashboardResponse.empty();
		}
		LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
		Instant now = Instant.now();
		List<TopAbsentSectionItem> topAbsent =
				loadTopAbsentSections(tenantId, todayUtc, sectionIds, DEFAULT_TOP_ABSENT_LIMIT);
		List<RecentSessionItem> recentClosed =
				loadRecentClosedSessions(tenantId, sectionIds, DEFAULT_RECENT_CLOSED_LIMIT);
		List<MySessionItem> mySectionsToday =
				loadMySectionsToday(tenantId, todayUtc, sectionIds, MY_SECTIONS_TODAY_LIMIT);
		TodayKpis kpis = loadTodayKpis(tenantId, todayUtc, sectionIds);
		log.debug("[teacher-dashboard] teacher={} sections={} rate={} open={} "
						+ "absent={} topAbsent={} recentClosed={} mySections={}",
				teacher.getPublicUuid(), sectionIds.size(), kpis.rate,
				kpis.open, kpis.absent, topAbsent.size(), recentClosed.size(),
				mySectionsToday.size());
		return new TeacherDashboardResponse(
				now,
				kpis.rate,
				kpis.enrollments,
				kpis.open,
				kpis.uniqueRegistered,
				kpis.absent,
				topAbsent,
				recentClosed,
				mySectionsToday);
	}

	// =====================================================================
	// Internal query helpers
	// =====================================================================

	/** KPIs of {@code today} scoped to the teacher's sections. */
	private TodayKpis loadTodayKpis(UUID tenantId, LocalDate today, List<UUID> sectionIds) {
		// Active sessions for the teacher on today.
		Long open = jdbc.queryForObject(
				"select count(*) from edushift.attendance_sessions s "
						+ "where s.tenant_id = :tid "
						+ "and s.status = 'ACTIVE' "
						+ "and s.occurred_on = :today "
						+ "and s.section_id in (:sectionIds)",
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("sectionIds", sectionIds),
				Long.class);
		// Enrollments in those sections (denominator of the rate).
		Long enrollments = jdbc.queryForObject(
				"select count(*) from edushift.student_enrollments e "
						+ "where e.tenant_id = :tid "
						+ "and e.status = 'ACTIVE' "
						+ "and e.section_id in (:sectionIds)",
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("sectionIds", sectionIds),
				Long.class);
		// Records PRESENT or LATE today.
		Long presentOrLate = jdbc.queryForObject(
				"select count(*) from edushift.attendance_records r "
						+ "join edushift.attendance_sessions s on s.id = r.session_id "
						+ "where r.tenant_id = :tid "
						+ "and s.occurred_on = :today "
						+ "and r.status in ('PRESENT','LATE') "
						+ "and s.section_id in (:sectionIds)",
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("sectionIds", sectionIds),
				Long.class);
		Long uniqueRegistered = jdbc.queryForObject(
				"select count(distinct r.student_id) from edushift.attendance_records r "
						+ "join edushift.attendance_sessions s on s.id = r.session_id "
						+ "where r.tenant_id = :tid "
						+ "and s.occurred_on = :today "
						+ "and s.section_id in (:sectionIds)",
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("sectionIds", sectionIds),
				Long.class);
		Long absent = jdbc.queryForObject(
				"select count(*) from edushift.attendance_records r "
						+ "join edushift.attendance_sessions s on s.id = r.session_id "
						+ "where r.tenant_id = :tid "
						+ "and s.occurred_on = :today "
						+ "and r.status = 'ABSENT' "
						+ "and s.section_id in (:sectionIds)",
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("sectionIds", sectionIds),
				Long.class);
		double rate = (enrollments == null || enrollments == 0L)
				? DashboardOverviewResponse.ZERO_RATE
				: Math.min(100.0,
						(presentOrLate == null ? 0L : presentOrLate) * 100.0
								/ (double) enrollments);
		return new TodayKpis(
				nullToZero(enrollments),
				nullToZero(open),
				nullToZero(uniqueRegistered),
				nullToZero(absent),
				rate);
	}

	/**
	 * Top absent sections within the last 7 days. Scoped to the
	 * teacher's sections via {@code section_id in (:sectionIds)}.
	 * Groups by section id; left-joins {@code sections} and the grade
	 * to denormalize the human-readable name.
	 */
	private List<TopAbsentSectionItem> loadTopAbsentSections(
			UUID tenantId, LocalDate today, List<UUID> sectionIds, int limit) {
		LocalDate since = today.minusDays(TOP_ABSENT_WINDOW_DAYS);
		String sql =
				"select sec.id as section_id, sec.public_uuid as section_uuid, sec.name as section_name, g.name as grade_name, "
						+ "       count(r.id) as absent_count "
						+ "from edushift.attendance_sessions s "
						+ "join edushift.attendance_records r on r.session_id = s.id "
						+ "join edushift.sections sec on sec.id = s.section_id "
						+ "left join edushift.grades g on g.id = sec.grade_id "
						+ "where r.tenant_id = :tid "
						+ "and r.status = 'ABSENT' "
						+ "and r.occurred_at >= :since "
						+ "and s.section_id in (:sectionIds) "
						+ "group by sec.id, sec.public_uuid, sec.name, g.name "
						+ "order by absent_count desc "
						+ "limit :lim";
		var rows = jdbc.queryForList(
				sql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("since", java.sql.Timestamp.from(since.atStartOfDay(ZoneOffset.UTC).toInstant()))
						.addValue("sectionIds", sectionIds)
						.addValue("lim", limit));
		// We need enrolled count per section to compute %. Bulk query.
		Map<UUID, Long> enrolledBySection = loadEnrolledBySection(tenantId, sectionIds);
		List<TopAbsentSectionItem> out = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			UUID sectionInternalId = (UUID) row.get("section_id");
			long absent = ((Number) row.get("absent_count")).longValue();
			out.add(new TopAbsentSectionItem(
					(UUID) row.get("section_uuid"),
					(String) row.get("section_name"),
					(String) row.get("grade_name"),
					absent,
					enrolledBySection.getOrDefault(sectionInternalId, 0L)));
		}
		return out;
	}

	private Map<UUID, Long> loadEnrolledBySection(UUID tenantId, List<UUID> sectionIds) {
		String sql =
				"select e.section_id as section_id, count(*) as enrolled "
						+ "from edushift.student_enrollments e "
						+ "where e.tenant_id = :tid "
						+ "and e.status = 'ACTIVE' "
						+ "and e.section_id in (:sectionIds) "
						+ "group by e.section_id";
		List<Map<String, Object>> rows = jdbc.queryForList(
				sql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("sectionIds", sectionIds));
		Map<UUID, Long> out = new HashMap<>(rows.size());
		for (Map<String, Object> row : rows) {
			// e.section_id is a JPA-internal UUID; we store it as a
			// string-serialized UUID in MapSqlParameterSource. Convert.
			Object raw = row.get("section_id");
			UUID sectionId = raw instanceof UUID ? (UUID) raw : UUID.fromString(raw.toString());
			out.put(sectionId, ((Number) row.get("enrolled")).longValue());
		}
		return out;
	}

	/**
	 * Recent closed sessions, scoped to the teacher's sections. We
	 * pull the latest N CLOSED sessions, then run a single
	 * {@code GROUP BY status} for the per-status counts (instead of 4
	 * separate queries per session, which would be N+1).
	 */
	private List<RecentSessionItem> loadRecentClosedSessions(
			UUID tenantId, List<UUID> sectionIds, int limit) {
		String sessionsSql =
				"select s.id, s.public_uuid, sec.public_uuid as section_uuid, sec.name as section_name, "
						+ "       s.occurred_on, s.slot, s.closed_at "
						+ "from edushift.attendance_sessions s "
						+ "join edushift.sections sec on sec.id = s.section_id "
						+ "where s.tenant_id = :tid "
						+ "and s.status = 'CLOSED' "
						+ "and s.section_id in (:sectionIds) "
						+ "order by s.closed_at desc nulls last "
						+ "limit :lim";
		List<Map<String, Object>> rows = jdbc.queryForList(
				sessionsSql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("sectionIds", sectionIds)
						.addValue("lim", limit));
		if (rows.isEmpty()) return List.of();
		// Batch the per-status counts.
		List<UUID> sessionUuids = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			sessionUuids.add((UUID) row.get("public_uuid"));
		}
		String countsSql =
				"select r.session_id as session_id, r.status, count(*) as cnt "
						+ "from edushift.attendance_records r "
						+ "where r.tenant_id = :tid "
						+ "and r.session_id in ("
						+ "    select id from edushift.attendance_sessions "
						+ "    where public_uuid in (:sessionUuids)) "
						+ "group by r.session_id, r.status";
		List<Map<String, Object>> countRows = jdbc.queryForList(
				countsSql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("sessionUuids", sessionUuids));
		Map<UUID, Map<String, Long>> countsBySession = new HashMap<>();
		for (Map<String, Object> row : countRows) {
			Object raw = row.get("session_id");
			UUID sid = raw instanceof UUID ? (UUID) raw : UUID.fromString(raw.toString());
			countsBySession
					.computeIfAbsent(sid, k -> new HashMap<>())
					.put((String) row.get("status"),
							((Number) row.get("cnt")).longValue());
		}
		List<RecentSessionItem> out = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			UUID sessionId = (UUID) row.get("public_uuid");
			Map<String, Long> c = countsBySession.getOrDefault(sessionId, Map.of());
			long present = c.getOrDefault(AttendanceRecordStatus.PRESENT.name(), 0L);
			long late = c.getOrDefault(AttendanceRecordStatus.LATE.name(), 0L);
			long absent = c.getOrDefault(AttendanceRecordStatus.ABSENT.name(), 0L);
			long excused = c.getOrDefault(AttendanceRecordStatus.EXCUSED.name(), 0L);
			out.add(new RecentSessionItem(
					sessionId,
					(UUID) row.get("section_uuid"),
					(String) row.get("section_name"),
					((java.sql.Date) row.get("occurred_on")).toLocalDate(),
					com.edushift.modules.attendance.entity.AttendanceSessionSlot
							.valueOf((String) row.get("slot")),
					((java.sql.Timestamp) row.get("closed_at")).toInstant(),
					present, late, absent, excused,
					present + late + absent + excused));
		}
		return out;
	}

	/**
	 * "My sections today" — for every section the teacher teaches,
	 * find the most recent session (if any) for {@code today}, with
	 * its status and per-status counts. Sections without a session
	 * today get a {@code PENDING} placeholder so the FE can render
	 * the "open session" CTA.
	 */
	private List<MySessionItem> loadMySectionsToday(
			UUID tenantId, LocalDate today, List<UUID> sectionIds, int limit) {
		// We pull all sections the teacher teaches (active enrollments
		// are usually a 1:1 match but we don't need to query it here;
		// FE renders "30/30" via the session detail or roster).
		String sectionsSql =
				"select sec.id, sec.public_uuid, sec.name "
						+ "from edushift.sections sec "
						+ "where sec.tenant_id = :tid "
						+ "and sec.id in ("
						+ "    select id from edushift.sections "
						+ "    where id in (:sectionIds)) "
						+ "order by sec.name asc";
		List<Map<String, Object>> sectionRows = jdbc.queryForList(
				sectionsSql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("sectionIds", sectionIds));
		// Session lookup for today: latest session for each section.
		String sessionSql =
				"select s.id, s.public_uuid, s.status, s.slot, s.closed_at "
						+ "from edushift.attendance_sessions s "
						+ "where s.tenant_id = :tid "
						+ "and s.occurred_on = :today "
						+ "and s.section_id in (:sectionIds) "
						+ "order by s.starts_at desc nulls last";
		List<Map<String, Object>> sessionRows = jdbc.queryForList(
				sessionSql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("sectionIds", sectionIds));
		Map<UUID, Map<String, Object>> sessionBySection = new HashMap<>();
		// We need latest per section: walk and keep first (order is desc starts_at).
		for (Map<String, Object> row : sessionRows) {
			Object raw = row.get("id");
			// we don't have section_id in the projection; add it
			// below via a separate projection.
			// For now, group by session id and look up section later.
		}
		// Re-query: include section_id so we can group.
		String sessionWithSectionSql =
				"select s.id as id, s.public_uuid, s.status, s.slot, s.closed_at, s.section_id as section_id "
						+ "from edushift.attendance_sessions s "
						+ "where s.tenant_id = :tid "
						+ "and s.occurred_on = :today "
						+ "and s.section_id in (:sectionIds) "
						+ "order by s.starts_at desc nulls last";
		List<Map<String, Object>> sessionRows2 = jdbc.queryForList(
				sessionWithSectionSql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("sectionIds", sectionIds));
		for (Map<String, Object> row : sessionRows2) {
			Object raw = row.get("section_id");
			UUID sectionInternalId = raw instanceof UUID ? (UUID) raw : UUID.fromString(raw.toString());
			if (!sessionBySection.containsKey(sectionInternalId)) {
				sessionBySection.put(sectionInternalId, row);
			}
		}
		// Per-status counts (same shape as recentClosed, limited to today's sessions).
		String countsSql =
				"select r.session_id, r.status, count(*) as cnt "
						+ "from edushift.attendance_records r "
						+ "where r.tenant_id = :tid "
						+ "and r.occurred_at >= :since "
						+ "and r.occurred_at < :until "
						+ "and r.session_id in ("
						+ "    select id from edushift.attendance_sessions "
						+ "    where tenant_id = :tid "
						+ "    and occurred_on = :today) "
						+ "group by r.session_id, r.status";
		List<Map<String, Object>> countRows = jdbc.queryForList(
				countsSql,
				new MapSqlParameterSource()
						.addValue("tid", tenantId)
						.addValue("today", today)
						.addValue("since", java.sql.Timestamp.from(today.atStartOfDay(ZoneOffset.UTC).toInstant()))
						.addValue("until", java.sql.Timestamp.from(today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())));
		Map<UUID, Map<String, Long>> countsBySession = new HashMap<>();
		for (Map<String, Object> row : countRows) {
			Object raw = row.get("session_id");
			UUID sid = raw instanceof UUID ? (UUID) raw : UUID.fromString(raw.toString());
			countsBySession
					.computeIfAbsent(sid, k -> new HashMap<>())
					.put((String) row.get("status"),
							((Number) row.get("cnt")).longValue());
		}
		// Enrolled per section (for the "N/N" pill).
		Map<UUID, Long> enrolledBySection = loadEnrolledBySection(tenantId, sectionIds);
		List<MySessionItem> out = new ArrayList<>();
		int count = 0;
		for (Map<String, Object> sRow : sectionRows) {
			if (count >= limit) break;
			Object rawSecId = sRow.get("id");
			UUID sectionInternalId = rawSecId instanceof UUID ? (UUID) rawSecId : UUID.fromString(rawSecId.toString());
			Map<String, Object> sessRow = sessionBySection.get(sectionInternalId);
			String status;
			UUID sessionPublicUuid = null;
			com.edushift.modules.attendance.entity.AttendanceSessionSlot slot = null;
			Instant closedAt = null;
			long present = 0L;
			long absent = 0L;
			if (sessRow == null) {
				status = AttendanceSessionStatus.CLOSED.name();
				// No session for this section today. PENDING-ish:
				// we don't have a real PENDING status enum; we use CLOSED
				// with a flag the FE can interpret. For MVP we mark as
				// "PENDING" semantically by leaving closedAt = null.
				status = "PENDING";
			} else {
				sessionPublicUuid = (UUID) sessRow.get("public_uuid");
				status = (String) sessRow.get("status");
				slot = com.edushift.modules.attendance.entity.AttendanceSessionSlot
						.valueOf((String) sessRow.get("slot"));
				Object rawClosed = sessRow.get("closed_at");
				closedAt = rawClosed == null ? null : ((java.sql.Timestamp) rawClosed).toInstant();
				Map<String, Long> c = countsBySession.getOrDefault(
						(UUID) sessRow.get("id"), Map.of());
				present = c.getOrDefault(AttendanceRecordStatus.PRESENT.name(), 0L);
				absent = c.getOrDefault(AttendanceRecordStatus.ABSENT.name(), 0L);
			}
			out.add(new MySessionItem(
					sessionPublicUuid,
					(UUID) sRow.get("public_uuid"),
					(String) sRow.get("name"),
					today,
					slot,
					status,
					enrolledBySection.getOrDefault(sectionInternalId, 0L),
					present,
					absent,
					closedAt));
			count++;
		}
		return out;
	}

	// =====================================================================
	// Small value types
	// =====================================================================

	private record TodayKpis(
			long enrollments,
			long open,
			long uniqueRegistered,
			long absent,
			double rate
	) {}

	private static long nullToZero(Long x) {
		return x == null ? 0L : x;
	}

	// suppress "unused" on imported Collections/Set/UUID that are reserved
	// for future re-use.
	@SuppressWarnings("unused")
	private static final Class<?>[] KEEP_REFS = {
			Collections.class, Set.class, HashSet.class,
			Section.class, AttendanceSession.class
	};
}
