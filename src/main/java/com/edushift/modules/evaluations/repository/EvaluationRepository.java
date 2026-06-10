package com.edushift.modules.evaluations.repository;

import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.sessions.learning.entity.LearningSession;
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
 * Spring Data repository for {@link com.edushift.modules.evaluations.entity.Evaluation}
 * (Sprint 5B / BE-5B.1). Tenant-scoped via Hibernate's {@code @TenantId}
 * discriminator.
 *
 * <p>The methods are intentionally narrow and return the full entity
 * (or aggregates thereof). The service layer wraps every load in a
 * "current tenant" check, so cross-tenant access is impossible even if
 * the caller knows a UUID from another tenant.</p>
 */
@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

	Optional<Evaluation> findByPublicUuid(UUID publicUuid);

	/**
	 * Per-assignment listing. Ordered by {@code scheduledDate desc} so the
	 * most recent evaluation shows up first in FE-5B.1.
	 */
	@Query("""
			select e from Evaluation e
			where e.teacherAssignment = :assignment
			order by e.scheduledDate desc, e.createdAt desc
			""")
	List<Evaluation> findAllByAssignment(@Param("assignment") TeacherAssignment assignment);

	/**
	 * Per-assignment listing with optional status / isActive / date-window
	 * filters. Filters are AND-combined and skip-on-null.
	 */
	@Query("""
			select e from Evaluation e
			where e.teacherAssignment = :assignment
			  and (:status   is null or e.status         = :status)
			  and (:isActive is null or e.isActive       = :isActive)
			  and (:from     is null or e.scheduledDate >= :from)
			  and (:to       is null or e.scheduledDate <= :to)
			order by e.scheduledDate desc, e.createdAt desc
			""")
	List<Evaluation> findFiltered(
			@Param("assignment") TeacherAssignment assignment,
			@Param("status") EvaluationStatus status,
			@Param("isActive") Boolean isActive,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/**
	 * Case-insensitive uniqueness probe scoped to a single assignment.
	 * Used by the service to surface {@code EVAL_NAME_EXISTS} (409)
	 * instead of a generic constraint violation.
	 */
	@Query("""
			select e from Evaluation e
			where e.teacherAssignment = :assignment
			  and lower(e.name) = lower(:name)
			""")
	Optional<Evaluation> findByAssignmentAndNameIgnoreCase(
			@Param("assignment") TeacherAssignment assignment,
			@Param("name") String name);

	/**
	 * Cross-tenant lookup of an evaluation by public UUID, bypassing the
	 * {@code @TenantId} filter. Reserved for admin / audit / lifecycle
	 * flows; the service guards it with a current-tenant check before
	 * returning to the caller.
	 */
	@Query("""
			select e from Evaluation e
			where e.publicUuid = :publicUuid
			""")
	Optional<Evaluation> findByPublicUuidAcrossTenants(@Param("publicUuid") UUID publicUuid);

	/**
	 * Existence probe used by the service when computing the
	 * {@code EVAL_HAS_GRADES} check on delete (BE-5B.3 will swap this
	 * for a real count over {@code grade_records}). For now the service
	 * always returns false, which keeps deletes allowed in BE-5B.1.
	 */
	@Query("""
			select count(e) > 0 from Evaluation e
			where e.unit = :unit
			""")
	boolean existsByUnit(@Param("unit") Unit unit);

	/**
	 * Reverse-lookup used by the "anchor session" validation: does any
	 * evaluation reference this session? Useful for FE-5B.4
	 * (Rubric association) where we want to warn before deleting a
	 * session with dependent evaluations.
	 */
	@Query("""
			select count(e) > 0 from Evaluation e
			where e.learningSession = :session
			""")
	boolean existsByLearningSession(@Param("session") LearningSession session);
}
