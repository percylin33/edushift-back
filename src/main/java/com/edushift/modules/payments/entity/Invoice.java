package com.edushift.modules.payments.entity;

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

/**
 * Invoice (Sprint 10 / BE-10.1).
 *
 * <p>A receivable document issued to a guardian for a specific
 * period. The source of truth for "what is owed". One or more
 * {@link Payment} rows may be attached; we mark the invoice
 * {@code PAID} when the sum of APPROVED payments equals or
 * exceeds {@code total_cents}.</p>
 *
 * <h3>Idempotency</h3>
 * The {@code idempotency_key} is unique per tenant. The cron uses
 * {@code "sub:<subscription-uuid>:<yyyy-MM>"} so a re-run for the
 * same month yields the same key, which is rejected by the unique
 * index — no double billing.
 */
@Entity
@Table(name = "invoices", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends TenantAwareEntity {

    public enum Status { PENDING, PAID, OVERDUE, CANCELLED, REFUNDED }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "guardian_user_id", nullable = false)
    private UUID guardianUserId;

    /** Unique per tenant. Cron-derived: {@code "sub:<uuid>:<yyyy-MM>"}. */
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "period_label", nullable = false, length = 40)
    private String periodLabel;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PEN";

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents = 0;

    @Column(name = "discount_cents", nullable = false)
    private long discountCents = 0;

    @Column(name = "tax_cents", nullable = false)
    private long taxCents = 0;

    @Column(name = "total_cents", nullable = false)
    private long totalCents = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.PENDING;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
