package com.edushift.modules.ai.entity;

import com.edushift.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/**
 * One row per LLM call (BE-7c.1). This is the audit log mandated by
 * {@code ai_rules.mdc} §AI AUDIT RULES: every call must be persisted
 * with prompt + response + tokens + model + status + tenant.
 *
 * <p>BE-7c.1 only emits rows in {@link Status#COMPLETED} or
 * {@link Status#FAILED}. The {@code PENDING} / {@code PROCESSING} /
 * {@code CANCELLED} states are reserved for the async job runner
 * (BE-7c.2).</p>
 *
 * <p>Extends {@link BaseEntity} directly (not {@code AuditableEntity})
 * because the audit log is system-generated — we do not track a
 * human creator/modifier on it.</p>
 */
@Entity
@Table(name = "ai_generations", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AiGeneration extends BaseEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "request_user_id")
    private UUID requestUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 60, updatable = false)
    private Feature feature;

    @Column(name = "prompt_text", nullable = false, updatable = false, columnDefinition = "text")
    private String promptText;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_parsed", columnDefinition = "jsonb")
    private Map<String, Object> responseParsed;

    @Column(name = "response_tokens")
    private Integer responseTokens;

    @Column(name = "model_used", length = 120)
    private String modelUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** AI feature that triggered the call. */
    public enum Feature {
        QUIZ_QUESTION_SUGGEST,
        RUBRIC_SUGGEST,
        SESSION_OUTLINE_SUGGEST,
        OTHER
    }

    /** Lifecycle status (mirrors {@code ai_rules.mdc}). */
    public enum Status {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Populates the {@code publicUuid} (used in API responses) before the
     * first insert if the caller did not set it explicitly. We do NOT
     * touch the inherited {@code id} (UUIDv7) — that is managed by
     * {@link BaseEntity} / {@code @UuidV7Id}.
     */
    @PrePersist
    private void onCreate() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }
}
