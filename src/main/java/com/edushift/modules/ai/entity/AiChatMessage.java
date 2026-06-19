package com.edushift.modules.ai.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI chat message (Sprint 8 / BE-8.3).
 *
 * <p>One row per message in a chat session. {@code role=user} comes
 * from the human, {@code role=assistant} from the LLM, and
 * {@code role=system} is the injected tenant context (invisible to
 * the FE; only metadata persisted for cost calculation).
 *
 * <p>Lifecycle: {@code PENDING} (row created, no content yet) →
 * {@code STREAMING} (SSE in progress, content updated incrementally)
 * → {@code COMPLETED} (final). On error: {@code FAILED}. On user
 * cancel: {@code CANCELLED}.
 *
 * @see AiChatSession
 */
@Entity
@Table(name = "ai_chat_messages", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class AiChatMessage extends TenantAwareEntity {

    /** Sender role. */
    public enum Role {
        /** Human message. Always visible to the user. */
        USER,
        /** Model response. */
        ASSISTANT,
        /** Tenant context injected by the backend. Hidden from FE. */
        SYSTEM
    }

    /** Streaming/lifecycle status. */
    public enum Status {
        /** Row created, awaiting LLM. */
        PENDING,
        /** SSE streaming in progress (only valid for role=ASSISTANT). */
        STREAMING,
        /** Final state. */
        COMPLETED,
        /** Streaming stopped due to an LLM error. */
        FAILED,
        /** User explicitly cancelled. */
        CANCELLED
    }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "chat_session_id", nullable = false)
    private UUID chatSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    /**
     * Threading hook. Reserved for v2 (branching conversations). In MVP v1
     * this is always null.
     */
    @Column(name = "parent_message_id")
    private UUID parentMessageId;

    /** Model id (e.g. {@code MiniMax/MiniMax-M2}) used for the assistant message. */
    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "response_tokens")
    private Integer responseTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** SEC-8.1: SHA-256 of the post-mask user text, hex-encoded. */
    @Column(name = "input_hash", length = 64)
    private String inputHash;

    /** SEC-8.1: SHA-256 of the post-mask assistant text, hex-encoded. */
    @Column(name = "output_hash", length = 64)
    private String outputHash;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }
}
