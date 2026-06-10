package com.edushift.modules.evaluations.graderecord.entity;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.students.entity.Student;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * <strong>GradeRecord</strong> — score (or qualitative literal) registered
 * by a teacher for a single {@link Student} against a single
 * {@link Evaluation}. Sprint 5B / BE-5B.3.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (evaluation, student)} is unique on non-deleted rows
 *       (DB-enforced via {@code uk_grade_records_evaluation_student}).</li>
 *   <li>At least one of {@code score} / {@code literal} must be non-null
 *       (DB-enforced via {@code chk_grade_records_value_present}).</li>
 *   <li>Per-scale shape (SCORE_0_20 → score; LITERAL_* → literal) is
 *       enforced by the service ({@code GRADE_SCORE_OUT_OF_RANGE},
 *       {@code GRADE_LITERAL_INVALID}); the DB only fences the gross
 *       numeric range and the global literal catalogue.</li>
 *   <li>Writes are accepted only when the parent evaluation is in
 *       {@code DRAFT} or {@code PUBLISHED}; {@code CLOSED} →
 *       {@code GRADE_EVAL_CLOSED} (409, service-enforced).</li>
 *   <li>The student must be ACTIVE-enrolled in the assignment's section
 *       on the evaluation's scheduled date or
 *       {@code GRADE_STUDENT_NOT_ENROLLED} (409, service-enforced).</li>
 * </ul>
 */
@Entity
@Table(
        name = "grade_records",
        schema = "edushift",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_grade_records_public_uuid",
                        columnNames = "public_uuid")
        },
        indexes = {
                @Index(name = "idx_grade_records_tenant_evaluation",
                        columnList = "tenant_id, evaluation_id"),
                @Index(name = "idx_grade_records_tenant_student",
                        columnList = "tenant_id, student_id"),
                @Index(name = "idx_grade_records_recorded_by",
                        columnList = "tenant_id, recorded_by_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
        of = {"publicUuid", "score", "literal", "recordedAt"})
@SQLDelete(sql = "UPDATE edushift.grade_records "
        + "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
        + "WHERE id = ?")
public class GradeRecord extends TenantAwareEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false,
            unique = true, columnDefinition = "uuid")
    private UUID publicUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_grade_records_evaluation"))
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_grade_records_student"))
    private Student student;

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "literal", length = 8)
    private String literal;

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by_user_id", nullable = false,
            columnDefinition = "uuid")
    private UUID recordedByUserId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    private void onPrePersist() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }
}
