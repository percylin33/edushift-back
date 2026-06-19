package com.edushift.modules.ai.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a caller requests an {@code ai_generations} row that
 * doesn't exist in their tenant (BE-7c.2). Mapped to HTTP 404.
 *
 * <p>Cross-tenant lookups land here too: the Hibernate {@code @TenantId}
 * filter hides rows from other tenants, so {@code findByPublicUuid}
 * returns {@code Optional.empty()} for a UUID that exists in tenant B
 * but is queried from tenant A. The client sees a 404 — indistinguishable
 * from "the row never existed" — which is the right behavior (no
 * information leak about other tenants' generation counts).</p>
 */
public class AiGenerationNotFoundException extends AiModuleException {

    public AiGenerationNotFoundException(UUID publicUuid) {
        super(HttpStatus.NOT_FOUND, "AI_GENERATION_NOT_FOUND",
                "AI generation not found: " + publicUuid);
    }
}
