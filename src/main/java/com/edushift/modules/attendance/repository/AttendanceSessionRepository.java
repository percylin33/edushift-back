package com.edushift.modules.attendance.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AttendanceSession}
 * (Sprint 6 / BE-6.1). Tenant-scoped via Hibernate's {@code @TenantId}
 * discriminator on {@link com.edushift.shared.domain.TenantAwareEntity}.
 *
 * <p>All methods are intentionally narrow. The service layer wraps every
 * load in a current-tenant check, so cross-tenant access is impossible
 * even if the caller knows a UUID from another tenant.
 */
@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

	Optional<AttendanceSession> findByPublicUuid(UUID publicUuid);

	/**
	 * Idempotency probe for {@code POST /attendance/sessions}: at most
	 * one ACTIVE session per {@code (section, occurredOn, slot)}. Used
	 * by the service to surface {@code SESSION_ALREADY_OPEN} (409)
	 * instead of relying on the DB to throw a constraint violation.
	 */
	@Query("""
			select s from AttendanceSession s
			where s.section = :section
			  and s.occurredOn = :occurredOn
			  and s.slot = :slot
			  and s.status = com.edushift.modules.attendance.entity.AttendanceSessionStatus.ACTIVE
			""")
	Optional<AttendanceSession> findActiveBySectionDaySlot(
			@Param("section") Section section,
			@Param("occurredOn") LocalDate occurredOn,
			@Param("slot") AttendanceSessionSlot slot);

	/**
	 * Per-section listing with optional date-window + status filters.
	 * Filters are AND-combined and skip-on-null. Drives the "Asistencia"
	 * sub-tab in section-detail (FE-6.2).
	 */
	@Query("""
			select s from AttendanceSession s
			where (:section is null or s.section = :section)
			  and (:status  is null or s.status  = :status)
			  and (:from    is null or s.occurredOn >= :from)
			  and (:to      is null or s.occurredOn <= :to)
			order by s.occurredOn desc, s.startsAt desc
			""")
	List<AttendanceSession> findFiltered(
			@Param("section") Section section,
			@Param("status") AttendanceSessionStatus status,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/**
	 * Last-N closed sessions across the tenant — drives the "Ultimas
	 * sesiones cerradas" widget on the admin dashboard (FE-6.4).
	 */
	@Query("""
			select s from AttendanceSession s
			where s.status = com.edushift.modules.attendance.entity.AttendanceSessionStatus.CLOSED
			order by s.closedAt desc
			""")
	List<AttendanceSession> findRecentClosed(org.springframework.data.domain.Pageable pageable);

	/**
	 * Count of active sessions for the day — KPI #2 on the admin
	 * dashboard.
	 */
	@Query("""
			select count(s) from AttendanceSession s
			where s.status = com.edushift.modules.attendance.entity.AttendanceSessionStatus.ACTIVE
			  and s.occurredOn = :day
			""")
	long countActiveOn(@Param("day") LocalDate day);
}
