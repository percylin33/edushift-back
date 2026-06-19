package com.edushift.modules.ai.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI chat session (Sprint 8 / BE-8.3).
 *
 * <p>Represents one conversation thread between a user (TEACHER or
 * TENANT_ADMIN) and the AI assistant. Memory is <b>in-session only</b>
 * (ADR-8.1, firmado 2026-06-18); cross-session memory is explicitly
 * out of scope for MVP v1 and pushed to Sprint 10+.
 *
 * <p>TTL: 7 days from {@code last_message_at}. The
 * {@code ChatSessionSweeper} job is responsible for soft-deleting
 * expired sessions and their messages.
 *
 * <p>Multi-tenant: extends {@link TenantAwareEntity} (UUIDv7 PK, soft
 * delete + audit cols). Cross-tenant access is prevented by Hibernate's
 * {@code @TenantId} discriminator. See audit report
 * {@code docs/qa/sprint-07b-multi-tenant-audit.md} for the conventions.
 *
 * @see AiChatMessage
 * @see com.edushift.modules.ai.service.ChatService
 */
@Entity
@Table(name = "ai_chat_sessions", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AiChatSession extends TenantAwareEntity {

    /** Lifecycle of a chat session. */
    public enum Status {
        /** Session is active and can receive new messages. */
        ACTIVE,
        /** User explicitly archived the session (read-only). */
        ARCHIVED,
        /** Soft-deleted; hidden from listings but kept for audit. */
        DELETED
    }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title = "Nueva conversacion";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "total_tokens_in", nullable = false)
    private int totalTokensIn = 0;

    @Column(name = "total_tokens_out", nullable = false)
    private int totalTokensOut = 0;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Update aggregates after a new message is appended.
     * Called from {@code ChatService} within the same transaction.
     */
    public void recordMessage(int tokensIn, int tokensOut) {
        this.messageCount++;
        this.totalTokensIn += tokensIn;
        this.totalTokensOut += tokensOut;
        this.lastMessageAt = Instant.now();
    }

    /**
     * Populate the public UUID before insert (mirrors the pattern in
     * {@code AiGeneration}). The internal {@code id} is managed by
     * {@code @UuidV7Id} on {@code BaseEntity}.
     */
    @PrePersist
    private void onCreate() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }
}
