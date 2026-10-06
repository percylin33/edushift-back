package com.edushift.modules.attendance.job;

import com.edushift.modules.attendance.service.AttendanceService;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * End-of-day cleanup: soft-deletes ACTIVE attendance sessions with
 * <strong>zero</strong> check-in records (no ABSENT materialization).
 *
 * <p>Protects gate-mode schools on holidays / unused days from leaving
 * orphan ACTIVE sessions, and pairs with empty-close semantics in
 * {@link AttendanceService#closeSession}.</p>
 *
 * <p>Morning-open of all sections is intentionally <strong>not</strong>
 * implemented here (requires an academic calendar).</p>
 *
 * <p>Cross-tenant discovery uses {@link JdbcTemplate} (no Hibernate
 * {@code @TenantId} filter) so the cron can run without a bound
 * tenant; per-tenant abandon runs inside {@code TenantContext.runAs}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		name = "edushift.attendance.abandon-unused.enabled",
		havingValue = "true",
		matchIfMissing = true)
public class AttendanceAbandonUnusedSessionsJob {

	private final JdbcTemplate jdbcTemplate;
	private final AttendanceService attendanceService;

	@Value("${edushift.attendance.abandon-unused.zone:America/Lima}")
	private String zoneId;

	/**
	 * Default 20:30 Mon–Fri (cron uses JVM default zone; business day
	 * uses {@link #zoneId}).
	 */
	@Scheduled(cron = "${edushift.attendance.abandon-unused.cron:0 30 20 * * MON-FRI}")
	public void abandonUnusedSessions() {
		LocalDate day = LocalDate.now(ZoneId.of(zoneId));
		List<UUID> tenantIds = jdbcTemplate.query(
				"""
						SELECT DISTINCT s.tenant_id
						FROM edushift.attendance_sessions s
						WHERE s.status = 'ACTIVE'
						  AND s.occurred_on = ?
						  AND s.deleted = false
						""",
				(rs, rowNum) -> (UUID) rs.getObject("tenant_id"),
				day);
		if (tenantIds.isEmpty()) {
			log.debug("[attendance-abandon] no ACTIVE sessions on day={}", day);
			return;
		}
		log.info("[attendance-abandon] scanning {} tenant(s) for unused ACTIVE sessions on {}",
				tenantIds.size(), day);
		int total = 0;
		for (UUID tenantId : tenantIds) {
			try {
				Integer abandoned = TenantContext.runAs(tenantId,
						() -> attendanceService.abandonUnusedSessionsForDay(day));
				total += abandoned == null ? 0 : abandoned;
			}
			catch (Exception ex) {
				log.error("[attendance-abandon] failed for tenant={} day={}: {}",
						tenantId, day, ex.getMessage(), ex);
			}
		}
		log.info("[attendance-abandon] done day={} abandoned={}", day, total);
	}
}
