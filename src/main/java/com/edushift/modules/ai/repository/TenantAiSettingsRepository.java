package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.TenantAiSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link TenantAiSettings} (BE-7c.1).
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator. There is at most 1 row per tenant; the
 * {@link #findByTenantId(UUID)} method returns the singleton for the
 * current tenant (or empty if not seeded yet).</p>
 *
 * <h3>Bug fix (post-Sprint 8)</h3>
 * The previous {@code findFirstByOrderByIdAsc()} returned the first
 * row in the table without filtering by tenant — so when a tenant
 * disabled AI, the master switch leaked to every other tenant.
 * Always use {@link #findByTenantId(UUID)} at the call site, and
 * pull {@code tenantId} from {@code TenantContext.currentRequired()}.
 */
@Repository
public interface TenantAiSettingsRepository extends JpaRepository<TenantAiSettings, UUID> {

    /**
     * Returns the singleton settings row for the given tenant.
     * Empty if the tenant has not been seeded yet (the V36 migration
     * seeds only the {@code demo} tenant; production seeds happen on
     * tenant creation).
     *
     * <p>Note: with the {@code @TenantId} Hibernate filter in effect
     * (set via {@code TenantContext}), Spring Data derived methods
     * auto-append {@code AND tenant_id = :currentTenant}. Passing the
     * same tenant id from {@code TenantContext.currentRequired()} is
     * belt-and-suspenders.</p>
     */
    Optional<TenantAiSettings> findByTenantId(UUID tenantId);

    @Query(value = """
            SELECT *
            FROM edushift.tenant_ai_settings
            WHERE tenant_id = :tenantId
              AND deleted = false
            """, nativeQuery = true)
    Optional<TenantAiSettings> findActiveByTenantId(@Param("tenantId") UUID tenantId);
}
