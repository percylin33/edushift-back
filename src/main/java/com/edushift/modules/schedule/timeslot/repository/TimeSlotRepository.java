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
}
