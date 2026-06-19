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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Payment (Sprint 10 / BE-10.1).
 *
 * <p>One row per payment attempt. The lifecycle is driven mostly
 * by MercadoPago webhooks:</p>
 *
 * <pre>
 *   PENDING (created)  → IN_PROCESS  (MP processing)
 *                     → APPROVED    (success, mark invoice PAID)
 *                     → REJECTED    (card declined / error)
 *                     → CANCELLED   (user / admin cancel)
 *   APPROVED → REFUNDED              (admin refund)
 * </pre>
 *
 * <h3>External id</h3>
 * The unique constraint on {@code (tenant_id, provider, external_id)}
 * makes a webhook replay safe: the second webhook with the same
 * {@code data.id} (MP payment_id) hits the unique index and we
 * upsert the existing row.
 */
@Entity
@Table(name = "payments", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends TenantAwareEntity {

    public enum Status {
        PENDING, IN_PROCESS, APPROVED, REJECTED, CANCELLED, REFUNDED
    }

    public enum Provider {
        MERCADOPAGO, MANUAL, CASH
    }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "guardian_user_id", nullable = false)
    private UUID guardianUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private Provider provider = Provider.MERCADOPAGO;

    @Column(name = "external_id", length = 80)
    private String externalId;

    /** We put the invoice public_uuid here so MP webhooks can find it. */
    @Column(name = "external_reference", length = 120)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PEN";

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @Column(name = "installments")
    private Integer installments;

    @Column(name = "paid_at")
    private Instant paidAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", nullable = false, columnDefinition = "jsonb")
    private String rawResponse = "{}";

    @Column(name = "failure_reason")
    private String failureReason;

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
