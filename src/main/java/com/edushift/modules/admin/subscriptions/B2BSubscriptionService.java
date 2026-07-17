package com.edushift.modules.admin.subscriptions;

import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.admin.subscriptions.B2BSubscription.B2BSubscriptionStatus;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.NotFoundException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2BSubscriptionService {

    private final B2BSubscriptionRepository subscriptionRepository;
    private final PlatformPlanRepository planRepository;

    public Optional<SubscriptionResponse> getActiveForTenant(UUID tenantId) {
        return subscriptionRepository.findByTenantIdAndStatusNot(tenantId, B2BSubscriptionStatus.CANCELED)
                .map(SubscriptionResponse::from);
    }

    @Transactional
    public SubscriptionResponse assignOrChangePlan(UUID tenantId, B2BSubscriptionRequest request) {
        PlatformPlan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));

        if (!plan.isActive()) {
            throw new NotFoundException("PLAN_NOT_FOUND", "Plan is not active");
        }

        B2BSubscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    B2BSubscription newSub = new B2BSubscription();
                    newSub.setTenantId(tenantId);
                    newSub.setCurrentPeriodStart(LocalDate.now());
                    newSub.setCurrentPeriodEnd(LocalDate.now().plusMonths(1));
                    newSub.setNextBillingAt(LocalDate.now().plusMonths(1));
                    newSub.setTrialEndsAt(request.trialEndsAt());
                    newSub.setStatus(B2BSubscriptionStatus.ACTIVE);
                    return newSub;
                });

        sub.setPlanId(request.planId());

        if (sub.getStatus() == B2BSubscriptionStatus.CANCELED
                || sub.getStatus() == B2BSubscriptionStatus.EXPIRED) {
            sub.setStatus(B2BSubscriptionStatus.ACTIVE);
            sub.setCurrentPeriodStart(LocalDate.now());
            sub.setCurrentPeriodEnd(LocalDate.now().plusMonths(1));
            sub.setNextBillingAt(LocalDate.now().plusMonths(1));
        }

        subscriptionRepository.save(sub);
        log.info("[b2b-subscription] plan changed for tenant={} to plan={}", tenantId, request.planId());

        return SubscriptionResponse.from(sub);
    }

    @Transactional
    public void cancel(UUID tenantId, String reason) {
        B2BSubscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("SUBSCRIPTION_NOT_FOUND", "No subscription found"));

        if (sub.isCancelAtPeriodEnd() || sub.getStatus() == B2BSubscriptionStatus.CANCELED) {
            throw new ConflictException("ALREADY_CANCELED", "Subscription is already canceled");
        }

        sub.setCancelAtPeriodEnd(true);
        sub.setCancellationReason(reason);
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void reactivate(UUID tenantId) {
        B2BSubscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("SUBSCRIPTION_NOT_FOUND", "No subscription found"));

        if (sub.getStatus() != B2BSubscriptionStatus.CANCELED) {
            throw new ConflictException("NOT_CANCELED", "Only canceled subscriptions can be reactivated");
        }

        long graceDays = java.time.Duration.between(
                sub.getCancelledAt() != null ? sub.getCancelledAt() : java.time.Instant.now(),
                java.time.Instant.now()).toDays();

        if (graceDays > 30) {
            throw new ConflictException("GRACE_PERIOD_EXPIRED",
                    "Cannot reactivate after 30 days of grace");
        }

        sub.setStatus(B2BSubscriptionStatus.ACTIVE);
        sub.setCancelAtPeriodEnd(false);
        sub.setCancelledAt(null);
        sub.setCancellationReason(null);
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void expireStale() {
        LocalDate threshold = LocalDate.now().minusDays(30);
        var expired = subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(
                B2BSubscriptionStatus.CANCELED, threshold);
        for (B2BSubscription sub : expired) {
            sub.setStatus(B2BSubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
        }
        if (!expired.isEmpty()) {
            log.info("[b2b-subscription] expired {} stale subscriptions", expired.size());
        }
    }
}
