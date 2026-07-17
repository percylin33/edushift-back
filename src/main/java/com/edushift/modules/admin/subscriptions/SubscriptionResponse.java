package com.edushift.modules.admin.subscriptions;

import com.edushift.modules.admin.subscriptions.B2BSubscription.B2BSubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        UUID planId,
        B2BSubscriptionStatus status,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDate trialEndsAt,
        boolean cancelAtPeriodEnd,
        Instant cancelledAt,
        String cancellationReason,
        LocalDate nextBillingAt
) {

    static SubscriptionResponse from(B2BSubscription sub) {
        return new SubscriptionResponse(
                sub.getId(), sub.getTenantId(), sub.getPlanId(), sub.getStatus(),
                sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd(), sub.getTrialEndsAt(),
                sub.isCancelAtPeriodEnd(), sub.getCancelledAt(), sub.getCancellationReason(),
                sub.getNextBillingAt());
    }
}
