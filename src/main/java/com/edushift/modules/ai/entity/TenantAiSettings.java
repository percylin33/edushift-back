package com.edushift.modules.ai.entity;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Per-tenant AI settings (BE-7c.1). Singleton-per-tenant (unique on
 * {@code tenantId}). See {@code V36__create_ai_module_tables.sql}.
 *
 * <p>This is the master switch and quota config. The {@code LmsAiService}
 * reads it on every call to decide whether the request is allowed
 * and how many tokens the tenant can spend this month.</p>
 *
 * <p>The {@code tenantId} is the tenant's public UUID, mirroring the
 * {@code lms_quizzes} and {@code lms_submissions} conventions. The
 * {@code @TenantId} Hibernate discriminator auto-filters by
 * {@code TenantContext}.</p>
 */
@Entity
@Table(name = "tenant_ai_settings", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class TenantAiSettings extends AuditableEntity {

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, unique = true)
    private UUID tenantId;

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    /** Nullable. NULL = unlimited within monthly_token_quota. */
    @Column(name = "daily_request_quota")
    private Integer dailyRequestQuota;

    /** Nullable. NULL = unlimited within daily_request_quota. */
    @Column(name = "monthly_token_quota")
    private Long monthlyTokenQuota;

    @Column(name = "default_model")
    private String defaultModel;

    /** Populate publicUuid on first insert. The id (UUIDv7) is managed by
     * {@code AuditableEntity} / {@code @UuidV7Id}. */
    @PrePersist
    private void onCreate() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }
}
