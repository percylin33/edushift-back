package com.edushift.modules.ai.entity;

import com.edushift.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Daily AI usage counter per tenant (BE-7c.1). One row per
 * {@code (tenantId, usageDay)}; upserted by the
 * {@code AiQuotaService} on every call. No soft-delete: old rows
 * age out via a 90-day TTL job (DEBT-BE-7C-1).
 *
 * <p>The {@code ai_rules.mdc} §TOKEN &amp; COST CONTROL mandates that
 * every call increments the counters; we batch the increment with
 * a single UPSERT in the same transaction as the LLM call so a
 * crash between LLM and DB still rolls back the call.</p>
 */
@Entity
@Table(name = "tenant_ai_usage", schema = "edushift",
       uniqueConstraints = @UniqueConstraint(name = "uq_tenant_ai_usage_tenant_day",
                                             columnNames = {"tenant_id", "usage_day"}))
@Getter
@Setter
@NoArgsConstructor
public class TenantAiUsage extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usage_day", nullable = false, updatable = false)
    private LocalDate usageDay;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "tokens_in_total", nullable = false)
    private long tokensInTotal;

    @Column(name = "tokens_out_total", nullable = false)
    private long tokensOutTotal;
}
