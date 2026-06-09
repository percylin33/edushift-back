package com.edushift.modules.sessions.learning.repository;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link LearningSession}. Tenant-scoped via
 * Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {

	Optional<LearningSession> findByPublicUuid(UUID publicUuid);

	/**
	 * Lists every active session of an assignment, ordered by
	 * {@code (scheduledDate asc, createdAt asc)}. Used by the
	 * assignment-scoped reverse view.
	 */
	@Query("""
			select s from LearningSession s
			where s.teacherAssignment = :assignment
			order by s.scheduledDate asc, s.createdAt asc
			""")
	List<LearningSession> findAllByAssignmentOrdered(
			@Param("assignment") TeacherAssignment assignment);

	/**
	 * Lists every active session of a unit, ordered by
	 * {@code (scheduledDate asc, createdAt asc)}. Used by the
	 * unit-scoped reverse view and by the "is this unit empty?" check
	 * on {@code UnitService.delete}.
	 */
	@Query("""
			select s from LearningSession s
			where s.unit = :unit
			order by s.scheduledDate asc, s.createdAt asc
			""")
	List<LearningSession> findAllByUnitOrdered(@Param("unit") Unit unit);

	/**
	 * Filtered list. All filters are AND-combined and skip-on-null.
	 *
	 * <p>Cross-context filters ({@code teacherId}, {@code sectionId},
	 * {@code periodId}) reach the assignment via inner joins. Hibernate
	 * applies the tenant filter on top of the JPQL automatically.</p>
	 */
	@Query("""
			select s from LearningSession s
			  join s.teacherAssignment a
			where (:teacherUuid is null or a.teacher.publicUuid        = :teacherUuid)
			  and (:sectionUuid is null or a.section.publicUuid        = :sectionUuid)
			  and (:periodUuid  is null or a.academicPeriod.publicUuid = :periodUuid)
			  and (:unitUuid    is null or s.unit.publicUuid           = :unitUuid)
			  and (:status      is null or s.status                    = :status)
			  and (:dateFrom    is null or s.scheduledDate             >= :dateFrom)
			  and (:dateTo      is null or s.scheduledDate             <= :dateTo)
			order by s.scheduledDate asc, s.createdAt asc
			""")
	List<LearningSession> findFiltered(
			@Param("teacherUuid") UUID teacherUuid,
			@Param("sectionUuid") UUID sectionUuid,
			@Param("periodUuid") UUID periodUuid,
			@Param("unitUuid") UUID unitUuid,
			@Param("status") SessionStatus status,
			@Param("dateFrom") LocalDate dateFrom,
			@Param("dateTo") LocalDate dateTo);

	/**
	 * Existence probe used by {@code UnitService.delete} to return
	 * {@code UNIT_HAS_SESSIONS} (409). Only counts non-cancelled
	 * sessions: a CANCELLED session is irrelevant for "is the unit
	 * still in use?".
	 */
	@Query("""
			select count(s) > 0 from LearningSession s
			where s.unit = :unit
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			""")
	boolean existsByUnit(@Param("unit") Unit unit);

	/**
	 * Per-unit count helper used by {@code UnitService.delete} when it
	 * wants the actual count for the error message ({@code "X sessions
	 * still reference it"}).
	 */
	@Query("""
			select count(s) from LearningSession s
			where s.unit = :unit
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			""")
	long countActiveByUnit(@Param("unit") Unit unit);

	/**
	 * Bulk count used by {@code UnitService.list} to render
	 * "X sessions in this unit" badges without an N+1 storm.
	 *
	 * <p>Returns rows of {@code (unit_id, count)}; callers typically
	 * collect into a {@code Map<UUID, Long>}.</p>
	 */
	@Query("""
			select s.unit.id, count(s) from LearningSession s
			where s.unit in :units
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			group by s.unit.id
			""")
	List<Object[]> countActiveByUnitIn(@Param("units") List<Unit> units);

	/**
	 * Per-competency count helper used by {@code CompetencyService.delete}.
	 */
	@Query("""
			select count(s) from LearningSession s
			  join s.competencies c
			where c = :competency
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			""")
	long countActiveByCompetency(@Param("competency") Competency competency);

	/**
	 * Per-capacity count helper used by {@code CapacityService.delete}.
	 */
	@Query("""
			select count(s) from LearningSession s
			  join s.capacities c
			where c = :capacity
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			""")
	long countActiveByCapacity(@Param("capacity") Capacity capacity);

	/**
	 * Existence probe used by {@code CompetencyService.delete}.
	 */
	@Query("""
			select count(s) > 0 from LearningSession s
			  join s.competencies c
			where c = :competency
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			""")
	boolean existsByCompetency(@Param("competency") Competency competency);

	/**
	 * Existence probe used by {@code CapacityService.delete}.
	 */
	@Query("""
			select count(s) > 0 from LearningSession s
			  join s.capacities c
			where c = :capacity
			  and s.status <> com.edushift.modules.sessions.learning.entity.SessionStatus.CANCELLED
			""")
	boolean existsByCapacity(@Param("capacity") Capacity capacity);
}
