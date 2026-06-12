package com.edushift.modules.attendance.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
	 * Per-section listing with optional date-window + status filters
	 * (Sprint 6 / BE-6.7). Filters are AND-combined and skip-on-null.
	 * The returned {@link Page} is tenant-scoped by Hibernate's
	 * {@code @TenantId} discriminator; cross-tenant leakage is
	 * structurally impossible regardless of inputs.
	 */
	@Query(value = """
			select s from AttendanceSession s
			where (:section is null or s.section = :section)
			  and (:status  is null or s.status  = :status)
			  and (:slot    is null or s.slot    = :slot)
			  and s.occurredOn >= coalesce(:from, cast('1900-01-01' as date))
			  and s.occurredOn <= coalesce(:to,   cast('2999-12-31' as date))
			""",
			countQuery = """
			select count(s) from AttendanceSession s
			where (:section is null or s.section = :section)
			  and (:status  is null or s.status  = :status)
			  and (:slot    is null or s.slot    = :slot)
			  and s.occurredOn >= coalesce(:from, cast('1900-01-01' as date))
			  and s.occurredOn <= coalesce(:to,   cast('2999-12-31' as date))
			""")
	Page<AttendanceSession> findFilteredPaged(
			@Param("section") Section section,
			@Param("status") AttendanceSessionStatus status,
			@Param("slot") AttendanceSessionSlot slot,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			Pageable pageable);

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

