package com.edushift.modules.schedule.timeslot.repository;

import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link TimeSlot}. Tenant-scoped via
 * Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

	Optional<TimeSlot> findByPublicUuid(UUID publicUuid);

	/**
	 * Lists every slot of an assignment, ordered by
	 * {@code (dayOfWeek asc, startTime asc)}.
	 */
	@Query("""
			select s from TimeSlot s
			where s.teacherAssignment = :assignment
			order by s.dayOfWeek asc, s.startTime asc
			""")
	List<TimeSlot> findAllByAssignmentOrdered(
			@Param("assignment") TeacherAssignment assignment);

	/**
	 * Cross-assignment lookup used by the reverse views (teacher /
	 * section schedule). Hibernate auto-applies the tenant filter on
	 * top of the IN clause.
	 */
	@Query("""
			select s from TimeSlot s
			where s.teacherAssignment in :assignments
			order by s.dayOfWeek asc, s.startTime asc
			""")
	List<TimeSlot> findAllByAssignmentInOrdered(
			@Param("assignments") List<TeacherAssignment> assignments);

	/**
	 * Overlap probe inside a single assignment + day.
	 *
	 * <p>Two ranges {@code (a, b)} and {@code (c, d)} overlap iff
	 * {@code a < d AND c < b}. We pass the candidate's
	 * {@code (start, end)} as {@code (c, d)} and let Postgres compute
	 * the rest. {@code excludeId} lets {@code update} ignore the row
	 * being modified.</p>
	 */
	@Query("""
			select s from TimeSlot s
			where s.teacherAssignment = :assignment
			  and s.dayOfWeek = :dayOfWeek
			  and s.startTime < :endTime
			  and :startTime < s.endTime
			  and (:excludeId is null or s.id <> :excludeId)
			""")
	List<TimeSlot> findOverlapping(
			@Param("assignment") TeacherAssignment assignment,
			@Param("dayOfWeek") Short dayOfWeek,
			@Param("startTime") LocalTime startTime,
			@Param("endTime") LocalTime endTime,
			@Param("excludeId") UUID excludeId);

	// ----------------------------------------------------------------------
	// Sprint cierre-C / B4 -- conflict detection queries.
	// Each query is tenant-scoped explicitly (defense-in-depth on top of
	// Hibernate's @TenantId on TimeSlot) and returns ALL slots in that
	// tenant + day; the caller (ScheduleConflictDetector) checks
	// time overlap in Java.
	// ----------------------------------------------------------------------

	/**
	 * All non-deleted slots in {@code tenantId} where the underlying
	 * teacher's publicUuid matches on the given day.
	 */
	@Query("""
			select s from TimeSlot s
			where s.tenantId = :tenantId
			  and s.deleted = false
			  and s.teacherAssignment.teacher.publicUuid = :teacherUuid
			  and s.dayOfWeek = :dayOfWeek
			""")
	List<TimeSlot> findByTenantIdAndTeacherAndDay(
			@Param("tenantId") UUID tenantId,
			@Param("teacherUuid") UUID teacherUuid,
			@Param("dayOfWeek") Short dayOfWeek);

	/**
	 * All non-deleted slots in {@code tenantId} pointing at
	 * {@code classroomId} on the given day (B4 FK).
	 */
	@Query("""
			select s from TimeSlot s
			where s.tenantId = :tenantId
			  and s.deleted = false
			  and s.classroomId = :classroomId
			  and s.dayOfWeek = :dayOfWeek
			""")
	List<TimeSlot> findByTenantIdAndClassroomAndDay(
			@Param("tenantId") UUID tenantId,
			@Param("classroomId") UUID classroomId,
			@Param("dayOfWeek") Short dayOfWeek);

	/**
	 * All non-deleted slots in {@code tenantId} carrying the legacy
	 * free-text {@code classroom} label on the given day. Pre-B4 slots
	 * that haven't been migrated to {@code classroomId} yet still go
	 * through the same conflict check.
	 */
	@Query("""
			select s from TimeSlot s
			where s.tenantId = :tenantId
			  and s.deleted = false
			  and s.classroom = :classroomLabel
			  and s.dayOfWeek = :dayOfWeek
			""")
	List<TimeSlot> findByTenantIdAndClassroomLabelAndDay(
			@Param("tenantId") UUID tenantId,
			@Param("classroomLabel") String classroomLabel,
			@Param("dayOfWeek") Short dayOfWeek);

	/**
	 * All non-deleted slots in {@code tenantId} attached to the same
	 * section (via the teacher assignment) on the given day.
	 */
	@Query("""
			select s from TimeSlot s
			where s.tenantId = :tenantId
			  and s.deleted = false
			  and s.teacherAssignment.section.publicUuid = :sectionUuid
			  and s.dayOfWeek = :dayOfWeek
			""")
	List<TimeSlot> findByTenantIdAndSectionAndDay(
			@Param("tenantId") UUID tenantId,
			@Param("sectionUuid") UUID sectionUuid,
			@Param("dayOfWeek") Short dayOfWeek);

	/**
	 * Active-assignment slots for a period (teacher workload aggregation).
	 * When {@code teacherUuid} is null, returns slots for every teacher.
	 */
	@Query("""
			select s from TimeSlot s
			where s.deleted = false
			  and s.teacherAssignment.unassignedAt is null
			  and s.teacherAssignment.academicPeriod.publicUuid = :periodUuid
			  and (:teacherUuid is null
			       or s.teacherAssignment.teacher.publicUuid = :teacherUuid)
			order by s.teacherAssignment.teacher.lastName asc,
			         s.dayOfWeek asc, s.startTime asc
			""")
	List<TimeSlot> findActiveByPeriodAndOptionalTeacher(
			@Param("periodUuid") UUID periodUuid,
			@Param("teacherUuid") UUID teacherUuid);
}
