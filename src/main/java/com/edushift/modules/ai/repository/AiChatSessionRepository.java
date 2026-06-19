package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.AiChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link AiChatSession} (Sprint 8 / BE-8.3).
 *
 * <p>All queries are auto-filtered by the current {@code TenantContext}
 * via the Hibernate {@code @TenantId} discriminator on
 * {@code TenantAwareEntity}. No method here ever accepts a
 * {@code tenantId} parameter (anti-enumeration + cross-tenant leak
 * prevention, per ai-rules.mdc).
 */
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {

    Optional<AiChatSession> findByPublicUuid(UUID publicUuid);

    /**
     * List the user's most-recent active sessions, paginated. Used by
     * the chat sidebar (FE-8.3).
     */
    @Query("""
            SELECT s FROM AiChatSession s
            WHERE s.userId = :userId
              AND s.status = com.edushift.modules.ai.entity.AiChatSession.Status.ACTIVE
            ORDER BY s.updatedAt DESC
            """)
    Page<AiChatSession> findActiveByUser(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Sessions past their TTL (used by {@code ChatSessionSweeper}).
     */
    @Query("""
            SELECT s FROM AiChatSession s
            WHERE s.expiresAt IS NOT NULL
              AND s.expiresAt < :cutoff
            """)
    List<AiChatSession> findExpired(@Param("cutoff") Instant cutoff, Pageable pageable);
}
