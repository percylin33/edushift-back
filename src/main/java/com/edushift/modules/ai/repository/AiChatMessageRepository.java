package com.edushift.modules.ai.repository;

import com.edushift.modules.ai.entity.AiChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link AiChatMessage} (Sprint 8 / BE-8.3).
 *
 * <p>Same tenant-isolation guarantee as {@code AiChatSessionRepository}:
 * Hibernate {@code @TenantId} auto-filters every query by
 * {@code TenantContext}. Cross-tenant access is impossible from these
 * methods.
 */
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {

    /**
     * Fetch all (non-deleted) messages of a session in chronological order.
     * Used to build the conversation history sent to the LLM and to
     * render the thread in the FE.
     */
    @Query("""
            SELECT m FROM AiChatMessage m
            WHERE m.chatSessionId = :sessionId
              AND m.role <> com.edushift.modules.ai.entity.AiChatMessage.Role.SYSTEM
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<AiChatMessage> findVisibleBySession(@Param("sessionId") UUID sessionId);

    /**
     * Same as above, but paginated (for very long conversations).
     */
    @Query("""
            SELECT m FROM AiChatMessage m
            WHERE m.chatSessionId = :sessionId
              AND m.role <> com.edushift.modules.ai.entity.AiChatMessage.Role.SYSTEM
            ORDER BY m.createdAt ASC
            """)
    Page<AiChatMessage> findVisibleBySessionPaged(@Param("sessionId") UUID sessionId, Pageable pageable);

    /**
     * Last N visible messages (for the LLM context window). Excludes
     * SYSTEM messages (those are injected by the backend, not the LLM).
     */
    @Query(value = """
            SELECT m FROM AiChatMessage m
            WHERE m.chatSessionId = :sessionId
              AND m.role <> com.edushift.modules.ai.entity.AiChatMessage.Role.SYSTEM
            ORDER BY m.createdAt DESC
            """)
    List<AiChatMessage> findLastVisibleBySession(@Param("sessionId") UUID sessionId, Pageable pageable);
}
