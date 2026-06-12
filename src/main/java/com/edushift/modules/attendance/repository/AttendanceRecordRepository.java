package com.edushift.modules.attendance.repository;

import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.students.entity.Student;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AttendanceRecord}
 * (Sprint 6 / BE-6.1). Tenant-scoped via Hibernate's {@code @TenantId}
 * discriminator. The {@code @SQLDelete} annotation on the entity makes
 * standard {@code delete()} a soft-delete; the partial unique index
 * {@code uk_attendance_records_session_student} on {@code WHERE NOT
 * deleted} releases the slot atomically when a row is soft-deleted.
 */
@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

	Optional<AttendanceRecord> findByPublicUuid(UUID publicUuid);

	/**
	 * Upsert helper: returns the existing non-deleted record for
	 * {@code (session, student)} so the service can decide between
	 * insert and update without colliding with the partial unique
	 * index.
	 */
	@Query("""
			select r from AttendanceRecord r
			where r.session = :session
			  and r.student = :student
			""")
	Optional<AttendanceRecord> findBySessionAndStudent(
			@Param("session") AttendanceSession session,
			@Param("student") Student student);

	/**
	 * All records of a session ordered by student name. Drives the
	 * roster view (REQ-ATT-04 / FE-6.2). The service joins this list
	 * with the section's enrollment to derive virtual {@code ABSENT}s
	 * while the session is ACTIVE.
	 */
	@Query("""
			select r from AttendanceRecord r
			join fetch r.student s
			where r.session = :session
			order by s.lastName, s.firstName
			""")
	List<AttendanceRecord> findBySessionOrderedByStudentName(
			@Param("session") AttendanceSession session);

	/**
	 * Per-student timeline (used by the "Asistencia" tab in
	 * student-detail and by Sprint 9 reports).
	 */
	@Query("""
			select r from AttendanceRecord r
			where r.student = :student
			  and (:from is null or r.occurredAt >= :from)
			  and (:to   is null or r.occurredAt <= :to)
			order by r.occurredAt desc
			""")
	List<AttendanceRecord> findByStudentInRange(
			@Param("student") Student student,
			@Param("from") Instant from,
			@Param("to") Instant to);

	/**
	 * Count of records by status in a window — drives the "today
	 * overview" KPIs of the admin dashboard.
	 */
	@Query("""
			select count(r) from AttendanceRecord r
			where r.status = :status
			  and r.occurredAt >= :from
			  and r.occurredAt <  :to
			""")
	long countByStatusInRange(
			@Param("status") AttendanceRecordStatus status,
			@Param("from") Instant from,
			@Param("to") Instant to);

	/**
	 * Count of records for a session by status (used when closing the
	 * session: {@code presentCount}, {@code lateCount}, {@code absentCount},
	 * {@code excusedCount}).
	 */
	@Query("""
			select count(r) from AttendanceRecord r
			where r.session = :session
			  and r.status  = :status
			""")
	long countBySessionAndStatus(
			@Param("session") AttendanceSession session,
			@Param("status") AttendanceRecordStatus status);

	/**
	 * Batched per-status counts for many sessions in one round trip
	 * (Sprint 6 / BE-6.7). Used by the listing endpoint to populate
	 * the four counters on each row of the page.
	 *
	 * <p>Returns one row per {@code (session, status)} pair that has
	 * at least one record. Sessions with no records are absent from
	 * the result — the caller treats missing sessions as zero
	 * counts. Tenant-scoped by Hibernate's {@code @TenantId}.</p>
	 */
	@Query("""
			select r.session, r.status, count(r)
			from AttendanceRecord r
			where r.session in :sessions
			group by r.session, r.status
			""")
	List<Object[]> countGroupedByStatusForSessions(
			@Param("sessions") List<AttendanceSession> sessions);
}
