package com.edushift.modules.qa;

import com.edushift.shared.domain.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * QA bug report created from the Centro de Pruebas ({@code /help}) FE wizard.
 *
 * <p>Append-only by design — status transitions happen via the PATCH endpoint,
 * never through {@code @SQLDelete} on the entity itself.</p>
 */
@Entity
@Table(name = "qa_bug_reports", schema = "edushift", indexes = {
        @Index(name = "idx_qa_bug_reports_tenant_capability", columnList = "tenant_id, capability_id"),
        @Index(name = "idx_qa_bug_reports_actor", columnList = "actor_id"),
        @Index(name = "idx_qa_bug_reports_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class QaBugReport extends AuditableEntity {

    @JsonIgnore
    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @JsonIgnore
    @Column(name = "actor_id", nullable = false, columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "capability_id", nullable = false, length = 200)
    private String capabilityId;

    @Column(name = "step_id", nullable = false, length = 200)
    private String stepId;

    @Column(name = "step_label", length = 500)
    private String stepLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request", columnDefinition = "jsonb")
    private Map<String, Object> request;

    public enum Severity {
        BLOCKER, MAJOR, MINOR, COSMETIC
    }

    public enum Status {
        OPEN, ACKNOWLEDGED, RESOLVED
    }
}