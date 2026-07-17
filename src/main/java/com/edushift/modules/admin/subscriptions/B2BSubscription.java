package com.edushift.modules.admin.subscriptions;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "b2b_subscriptions", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class B2BSubscription extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private B2BSubscriptionStatus status = B2BSubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    @Column(name = "next_billing_at", nullable = false)
    private LocalDate nextBillingAt;

    public enum B2BSubscriptionStatus {
        TRIAL, ACTIVE, PAST_DUE, CANCELED, EXPIRED
    }
}
