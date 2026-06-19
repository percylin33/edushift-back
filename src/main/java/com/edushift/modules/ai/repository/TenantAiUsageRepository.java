package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.TenantAiUsage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link TenantAiUsage} (BE-7c.1).
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator; cross-tenant lookups return empty by construction.</p>
 *
 * <p>The {@link #incrementCounters} query is a single native UPSERT
 * (PostgreSQL {@code ON CONFLICT ... DO UPDATE}) so we can bump the
 * counters atomically in one round-trip. The alternative (read +
 * modify + save) is racy under concurrent calls.</p>
 */
@Repository
public interface TenantAiUsageRepository extends JpaRepository<TenantAiUsage, UUID> {

    Optional<TenantAiUsage> findByUsageDay(LocalDate usageDay);

    /**
     * Atomic upsert of the daily counters for the current tenant.
     * Called inside a transaction by the {@code AiQuotaService}.
     *
     * @param tenantId    the tenant's public UUID (injected by the
     *                    service from {@code TenantContext}).
     * @param usageDay    the UTC day to count toward.
     * @param requestDelta added to {@code request_count} (always +1).
     * @param successDelta added to {@code success_count} (0 or 1).
     * @param failedDelta  added to {@code failed_count} (0 or 1).
     * @param tokensIn     added to {@code tokens_in_total}.
     * @param tokensOut    added to {@code tokens_out_total}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO edushift.tenant_ai_usage
                (id, tenant_id, usage_day, request_count, success_count, failed_count,
                 tokens_in_total, tokens_out_total, created_at, updated_at, deleted)
            VALUES
                (gen_random_uuid(), :tenantId, :usageDay, :requestDelta, :successDelta, :failedDelta,
                 :tokensIn, :tokensOut, now(), now(), false)
            ON CONFLICT (tenant_id, usage_day) DO UPDATE
               SET request_count    = edushift.tenant_ai_usage.request_count    + EXCLUDED.request_count,
                   success_count    = edushift.tenant_ai_usage.success_count    + EXCLUDED.success_count,
                   failed_count     = edushift.tenant_ai_usage.failed_count     + EXCLUDED.failed_count,
                   tokens_in_total  = edushift.tenant_ai_usage.tokens_in_total  + EXCLUDED.tokens_in_total,
                   tokens_out_total = edushift.tenant_ai_usage.tokens_out_total + EXCLUDED.tokens_out_total,
                   updated_at       = now()
            """, nativeQuery = true)
    void incrementCounters(
            @Param("tenantId") UUID tenantId,
            @Param("usageDay") LocalDate usageDay,
            @Param("requestDelta") int requestDelta,
            @Param("successDelta") int successDelta,
            @Param("failedDelta") int failedDelta,
            @Param("tokensIn") long tokensIn,
            @Param("tokensOut") long tokensOut
    );

    /**
     * Sum of {@code tokens_in_total + tokens_out_total} for the current
     * UTC month, used to enforce {@code monthly_token_quota}. Returns 0
     * if no rows exist for the month.
     */
    @Query(value = """
            SELECT COALESCE(SUM(tokens_in_total + tokens_out_total), 0)
            FROM   edushift.tenant_ai_usage
            WHERE  tenant_id = :tenantId
              AND  usage_day >= date_trunc('month', now() AT TIME ZONE 'UTC')::date
              AND  usage_day <  date_trunc('month', now() AT TIME ZONE 'UTC')::date + INTERVAL '1 month'
            """, nativeQuery = true)
    long sumTokensThisMonth(@Param("tenantId") UUID tenantId);

    /**
     * TTL job: hard-delete rows older than the cutoff (DEBT-BE-7C-1).
     *
     * <p>Returns the number of rows actually deleted (useful for
     * tests and operator dashboards). Uses {@code created_at} (not
     * {@code usage_day}) as the "age" clock because {@code created_at}
     * is the audit timestamp set by the {@code BaseEntity} machinery;
     * {@code usage_day} is the day being counted, not the day the
     * row was inserted.</p>
     *
     * <p>Hard delete (not soft) because this is a derived counter:
     * losing the row loses nothing we don't already have aggregated
     * in today's counter row.</p>
     *
     * <p><strong>Why no @TenantId filter on this query:</strong> same
     * rationale as {@code AiSweeper} — this is a system job that must
     * age out rows across ALL tenants. The sweeper job
     * ({@code AiSweeper}) bypasses the filter with a native query and
     * {@code entityManager.clear()}; we do the same here.</p>
     */
    @Modifying
    @Query(value = """
            DELETE FROM edushift.tenant_ai_usage
            WHERE created_at < :cutoff
            """, nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
