package com.edushift.modules.evaluations.graderecord.repository;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.students.entity.Student;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link GradeRecord}. All queries are
 * tenant-aware via {@code @TenantId} on the entity (Hibernate filters
 * automatically by {@code tenant_id} when a {@code TenantContext} is set).
 */
@Repository
public interface GradeRecordRepository extends JpaRepository<GradeRecord, UUID> {

    /**
     * Loads a record by its public UUID under the active tenant.
     */
    Optional<GradeRecord> findByPublicUuid(UUID publicUuid);

    /**
     * Looks up the (eval, student) pair to drive upsert semantics. Returns
     * empty if no active row exists for the pair.
     */
    @Query("""
            select g from GradeRecord g
            where g.evaluation = :evaluation
              and g.student    = :student
            """)
    Optional<GradeRecord> findByEvaluationAndStudent(
            @Param("evaluation") Evaluation evaluation,
            @Param("student") Student student);

    /**
     * Lists all grades for an evaluation under the active tenant.
     */
    @Query("""
            select g from GradeRecord g
            where g.evaluation.publicUuid = :evaluationPublicUuid
            order by g.student.lastName asc, g.student.firstName asc
            """)
    List<GradeRecord> findAllByEvaluationPublicUuid(
            @Param("evaluationPublicUuid") UUID evaluationPublicUuid);

    /**
     * Lists all grades for a single student across the active tenant.
     */
    @Query("""
            select g from GradeRecord g
            where g.student.publicUuid = :studentPublicUuid
            order by g.recordedAt desc
            """)
    List<GradeRecord> findAllByStudentPublicUuid(
            @Param("studentPublicUuid") UUID studentPublicUuid);

    /**
     * Lists grades whose parent evaluation's assignment belongs to a
     * given section. Drives the per-section grade book filter.
     */
    @Query("""
            select g from GradeRecord g
            where g.evaluation.teacherAssignment.section.publicUuid
                  = :sectionPublicUuid
            order by g.evaluation.scheduledDate desc, g.recordedAt desc
            """)
    List<GradeRecord> findAllBySectionPublicUuid(
            @Param("sectionPublicUuid") UUID sectionPublicUuid);

    /**
     * Filtered list against a single evaluation. Any null filter means
     * "don't filter on this dimension".
     */
    @Query("""
            select g from GradeRecord g
            where g.evaluation.publicUuid = :evaluationPublicUuid
              and (:studentPublicUuid is null
                   or g.student.publicUuid = :studentPublicUuid)
              and (:sectionPublicUuid is null
                   or g.evaluation.teacherAssignment.section.publicUuid
                      = :sectionPublicUuid)
              and (:isActive is null or g.isActive = :isActive)
            order by g.student.lastName asc, g.student.firstName asc
            """)
    List<GradeRecord> findFilteredByEvaluation(
            @Param("evaluationPublicUuid") UUID evaluationPublicUuid,
            @Param("studentPublicUuid") UUID studentPublicUuid,
            @Param("sectionPublicUuid") UUID sectionPublicUuid,
            @Param("isActive") Boolean isActive);

    /**
     * Drives the {@code EVAL_HAS_GRADES} guard in
     * {@code EvaluationServiceImpl.deleteEvaluation} (BE-5B.3 closes the
     * placeholder previously left by BE-5B.1).
     */
    @Query("""
            select count(g) > 0 from GradeRecord g
            where g.evaluation = :evaluation
            """)
    boolean existsByEvaluation(@Param("evaluation") Evaluation evaluation);

    /**
     * Counts active grades for an evaluation. Used by BE-5B.4 to plug the
     * real {@code gradeCount} into {@code EvaluationResponse}.
     */
    @Query("""
            select count(g) from GradeRecord g
            where g.evaluation = :evaluation
            """)
    long countByEvaluation(@Param("evaluation") Evaluation evaluation);
}
