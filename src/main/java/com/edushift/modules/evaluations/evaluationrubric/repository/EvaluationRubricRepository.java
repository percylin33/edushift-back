package com.edushift.modules.evaluations.evaluationrubric.repository;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.evaluationrubric.entity.EvaluationRubric;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link EvaluationRubric} (Sprint 5B / BE-5B.4).
 *
 * <p>All queries are filtered by:</p>
 * <ul>
 *   <li>{@code @TenantId} on the entity (Hibernate adds
 *       {@code WHERE tenant_id = :currentTenant} when {@code TenantContext}
 *       is set).</li>
 *   <li>{@code @SQLRestriction("deleted = false")} inherited from
 *       {@code BaseEntity} (soft-deleted rows are invisible to JPQL).</li>
 * </ul>
 *
 * <p>For the join-table semantics this means that
 * {@code findActiveByEvaluation} only ever returns the currently active
 * association — even if previous attachments were re-pointed via the
 * upsert flow.</p>
 */
@Repository
public interface EvaluationRubricRepository
        extends JpaRepository<EvaluationRubric, UUID> {

    /**
     * Returns the currently active rubric attachment for the given
     * evaluation under the active tenant.
     *
     * <p>The MVP guarantees 0..1 results via the partial unique index
     * {@code uk_evaluation_rubric_evaluation}; we still return
     * {@code Optional} for explicit null-safety.</p>
     */
    @Query("""
            select er from EvaluationRubric er
            where er.evaluation = :evaluation
            """)
    Optional<EvaluationRubric> findActiveByEvaluation(
            @Param("evaluation") Evaluation evaluation);

    /**
     * Same as {@link #findActiveByEvaluation(Evaluation)} but keyed on
     * the evaluation's public UUID — used by the controller to skip a
     * separate {@code findByPublicUuid} round-trip when only the rubric
     * payload is needed.
     */
    @Query("""
            select er from EvaluationRubric er
            where er.evaluation.publicUuid = :evaluationPublicUuid
            """)
    Optional<EvaluationRubric> findActiveByEvaluationPublicUuid(
            @Param("evaluationPublicUuid") UUID evaluationPublicUuid);

    /**
     * Drives the {@code RUBRIC_IN_USE_BY_EVALUATIONS} guard in
     * {@code RubricServiceImpl.deleteRubric} — true if any evaluation
     * (under the active tenant) still has this rubric attached.
     */
    @Query("""
            select count(er) > 0 from EvaluationRubric er
            where er.rubric = :rubric
            """)
    boolean existsByRubric(@Param("rubric") Rubric rubric);

    /**
     * Counts active rubric attachments for a rubric. Surfaces in the
     * conflict payload of {@code RUBRIC_IN_USE_BY_EVALUATIONS} so the
     * caller can show "X evaluaciones la tienen asociada".
     */
    @Query("""
            select count(er) from EvaluationRubric er
            where er.rubric = :rubric
            """)
    long countByRubric(@Param("rubric") Rubric rubric);
}
