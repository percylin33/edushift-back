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
 * Subscription (Sprint 10 / BE-10.1).
 *
 * <p>A recurring billing plan attached to one student and one
 * guardian. The {@code InvoiceCronJob} reads
 * {@code subscriptions WHERE status = ACTIVE AND next_billing_at <= now}
 * and issues the next invoice, then advances {@code next_billing_at}
 * by one period.</p>
 *
 * <p>The actual amount billed is {@code amount_cents} (per period,
 * not per child) — the MVP model is "family tuition", not "per
 * course". If we later need per-course fees, we can add a
 * {@code subscription_items} child table.</p>
 */
@Entity
@Table(name = "subscriptions", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends TenantAwareEntity {

    public enum Status { ACTIVE, PAUSED, CANCELLED }
    public enum BillingPeriod { MONTHLY, ANNUAL }

    @Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
    private UUID publicUuid;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "guardian_user_id", nullable = false)
    private UUID guardianUserId;

    @Column(name = "plan_code", nullable = false, length = 40)
    private String planCode;

    /** Recurring amount in minor units (centavos PEN). */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "PEN";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 10)
    private BillingPeriod billingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.ACTIVE;

    @Column(name = "start_at", nullable = false)
    private Instant startAt = Instant.now();

    @Column(name = "next_billing_at")
    private Instant nextBillingAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @PrePersist
    private void onCreate() {
        if (publicUuid == null) publicUuid = UUID.randomUUID();
    }
}
