package com.edushift.modules.evaluations.evaluationrubric.entity;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * <strong>EvaluationRubric</strong> — join row connecting an
 * {@link Evaluation} with the {@link Rubric} it should be scored
 * against. Sprint 5B / BE-5B.4.
 *
 * <h3>Cardinality</h3>
 * Modelled as M:N (per ADR-5B.6 in the sprint plan: "use M:N table
 * to keep the door open to multi-rubric per evaluation in the future,
 * DEBT-EVAL-6"), but in the MVP a partial unique index on
 * {@code evaluation_id} (where {@code NOT deleted}) enforces 0..1
 * rubric per evaluation. Re-attaching a rubric (POST {@code /rubric})
 * soft-deletes the previous row and inserts a new one — no row is
 * mutated in-place, so the audit trail is preserved.
 *
 * <h3>Why no public_uuid</h3>
 * The link is operated through the parent evaluation
 * ({@code POST/GET/DELETE /v1/evaluations/{publicUuid}/rubric});
 * it is not a first-class resource that the API exposes by id.
 *
 * <h3>Multi-tenant</h3>
 * Tenant isolation is enforced by Hibernate's {@code @TenantId}
 * filter on {@link TenantAwareEntity}. The service layer additionally
 * cross-validates that the {@link Rubric} belongs to the same tenant
 * as the {@link Evaluation} before persisting (defence in depth).
 */
@Entity
@Table(
        name = "evaluation_rubric",
        schema = "edushift",
        indexes = {
                @Index(name = "idx_evaluation_rubric_tenant_rubric",
                        columnList = "tenant_id, rubric_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@SQLDelete(sql = "UPDATE edushift.evaluation_rubric "
        + "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
        + "WHERE id = ?")
public class EvaluationRubric extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_evaluation_rubric_evaluation"))
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_id", nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_evaluation_rubric_rubric"))
    private Rubric rubric;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
