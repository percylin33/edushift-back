package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.TenantAiSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link TenantAiSettings} (BE-7c.1).
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator. There is at most 1 row per tenant; the
 * {@code findFirstByOrderByIdAsc} returns the singleton (or empty).</p>
 */
@Repository
public interface TenantAiSettingsRepository extends JpaRepository<TenantAiSettings, UUID> {

    /** Returns the singleton settings row for the current tenant. Empty
     * if the tenant has not been seeded yet (the V36 migration seeds
     * only the {@code demo} tenant; production seeds happen on tenant
     * creation). */
    Optional<TenantAiSettings> findFirstByOrderByIdAsc();
}
