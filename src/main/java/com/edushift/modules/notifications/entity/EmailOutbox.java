package com.edushift.modules.notifications.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Email outbox (Sprint 9 / BE-9.1, ADR-9.1).
 *
 * <p>One row per email to send. The {@code EmailOutboxProcessor} job
 * (scheduled every 30s) picks up {@code PENDING} rows whose
 * {@code next_retry_at <= now} and dispatches them via {@code JavaMailSender}.
 * On failure the row is re-queued with exponential backoff
 * ({@code 2^attempts} minutes, capped at 5 retries before marking
 * {@code FAILED}).</p>
 *
 * <h3>Why a DB outbox and not Redis/queue</h3>
 * - Already have Postgres; no new infra.
 * - Survives restarts (in-memory queue would lose emails on crash).
 * - Can be inspected/audited by ops with a simple {@code SELECT}.
 * - Throughput is enough for MVP (school notifications, not millions/day).
 *
 * <h3>Multi-tenant</h3>
 * Extends {@link TenantAwareEntity}. The processor is a system job that
 * touches all tenants; we bypass the {@code @TenantId} filter with a
 * native query (similar pattern to {@code AiSweeper}).
 */
@Entity
@Table(name = "email_outbox", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox extends TenantAwareEntity {

    public enum Status {
        /** Awaiting send. */
        PENDING,
        /** Successfully sent (mail server accepted the message). */
        SENT,
        /** Permanently failed (max retries reached, or validation error). */
        FAILED
    }

    /** Max retries before marking FAILED. After 5 attempts, give up. */
    public static final int MAX_ATTEMPTS = 5;

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    /** Optional backref to the {@code Notification} that produced this email. */
    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    /** HTML body (sanitized). */
    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }

    /**
     * Compute the next retry instant using exponential backoff.
     * Called by the processor on failure: {@code 2^attempts minutes}.
     * At attempt 0 → 1 min. At attempt 4 → 16 min. Capped at 60 min.
     */
    public Instant computeNextRetryAt() {
        int minutes = (int) Math.min(60, Math.pow(2, Math.max(0, attempts)));
        return Instant.now().plus(Duration.ofMinutes(minutes));
    }
}
