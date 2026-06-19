package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * In-app notification (Sprint 9 / BE-9.1).
 *
 * <p>One row per delivery (recipient + template + payload). Lifecycle:
 * {@link Status#PENDING} → {@link Status#SENT} → ({@link Status#DELIVERED} via
 * polling) → {@link Status#READ} when the user opens the bell.
 * If the user opts out via preferences, the row is created with
 * {@link Status#SKIPPED} instead of SENT (audit trail preserved).
 *
 * <p>{@code payload} is a JSONB column holding the event data (e.g.
 * {@code {"studentName": "...", "date": "2026-06-19", "reason": "..."}}).
 * The {@code TemplateEngine} expands the placeholders in the template body
 * with the payload fields.</p>
 *
 * <h3>Multi-tenant</h3>
 * Extends {@link TenantAwareEntity}; Hibernate {@code @TenantId} auto-filters.
 * Anti-enumeration: queries are auto-scoped; cross-tenant lookups return empty.
 *
 * <h3>FK strategy</h3>
 * {@code recipient_user_id} → {@code users(id)} ON DELETE CASCADE. If a user
 * leaves the tenant, their notifications go with them (no orphan rows in the
 * audit log).
 */
@Entity
@Table(name = "notifications", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends TenantAwareEntity {

    /** Delivery channel(s) for this notification. */
    public enum Channel {
        /** In-app only (badge + bell). */
        IN_APP,
        /** Email only (via outbox). */
        EMAIL,
        /** Both channels (default for critical events). */
        BOTH
    }

    /** Lifecycle status. */
    public enum Status {
        /** Row created, awaiting processing (e.g. template expansion). */
        PENDING,
        /** Successfully dispatched (in-app stored, email enqueued). */
        SENT,
        /** Acknowledged by the user opening the bell (FE-9.1 PATCH /read). */
        READ,
        /** User opted out via preferences. Audit row preserved. */
        SKIPPED,
        /** Permanent failure (template missing, validation error). */
        FAILED
    }

    /** Notification category (mirrors {@code notification_preferences.category}). */
    public enum Category {
        ABSENCE, GRADE, QUIZ, TASK, AI_FEEDBACK, ANNOUNCEMENT, PAYMENT, SYSTEM
    }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "template_key", nullable = false, length = 60)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private Channel channel = Channel.IN_APP;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
