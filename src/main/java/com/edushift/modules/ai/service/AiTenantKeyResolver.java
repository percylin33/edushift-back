package com.edushift.modules.ai.service;

import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.tenants.repository.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the tenant key used by AI quota tables.
 *
 * <p>{@code tenant_ai_settings.tenant_id} and {@code tenant_ai_usage.tenant_id}
 * reference {@code tenants.public_uuid}, while {@link com.edushift.shared.multitenancy.TenantContext}
 * and JWTs carry {@code tenants.id}. Hibernate {@code @TenantId} filtering on AI
 * entities therefore needs the public UUID, not the internal id (DEBT-BE-7B-4).</p>
 */
@Component
public class AiTenantKeyResolver {

    private final TenantRepository tenantRepository;

    public AiTenantKeyResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public UUID resolve(UUID internalTenantId) {
        return tenantRepository.findById(internalTenantId)
                .map(t -> t.getPublicUuid())
                .orElseThrow(AiDisabledException::new);
    }
}
