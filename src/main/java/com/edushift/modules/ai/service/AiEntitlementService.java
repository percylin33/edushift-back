package com.edushift.modules.ai.service;

import com.edushift.modules.admin.plans.PlanAiPolicy;
import com.edushift.modules.admin.plans.PlanAiPolicyRepository;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.admin.subscriptions.B2BSubscription;
import com.edushift.modules.admin.subscriptions.B2BSubscriptionRepository;
import com.edushift.modules.ai.exception.AiFeatureDisabledException;
import com.edushift.modules.ai.exception.AiPlanUnavailableException;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves AI rights from the authenticated tenant, its B2B subscription and plan. */
@Service
@RequiredArgsConstructor
public class AiEntitlementService {

    public static final String GENERATE = "AI_GENERATE";
    public static final String TUTOR = "AI_TUTOR";
    public static final String TEACHER = "AI_TEACHER";
    public static final String FAMILY = "AI_FAMILY";
    public static final String RAG_CORE = "AI_RAG_CORE";
    public static final String RAG_SCHOOL_DOCS = "AI_RAG_SCHOOL_DOCS";
    public static final String RAG_BULK_IMPORT = "AI_RAG_BULK_IMPORT";
    public static final String PROMPT_CUSTOMIZATION = "AI_PROMPT_CUSTOMIZATION";

    private final TenantRepository tenantRepository;
    private final B2BSubscriptionRepository subscriptionRepository;
    private final PlatformPlanRepository planRepository;
    private final PlanAiPolicyRepository policyRepository;

    @Transactional(readOnly = true)
    public EffectiveAiPlan current() {
        UUID tenantId = TenantContext.currentRequired();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(AiPlanUnavailableException::new);
        if (tenant.getStatus() != TenantStatus.ACTIVE || tenant.getPlanId() == null) {
            throw new AiPlanUnavailableException();
        }

        // Legacy tenants may predate B2B subscriptions. Their persisted plan_id
        // remains authoritative until a subscription is assigned.
        boolean legacyWithoutSubscription = true;
        var subscription = subscriptionRepository.findByTenantId(tenantId);
        if (subscription.isPresent()) {
            B2BSubscription sub = subscription.get();
            legacyWithoutSubscription = false;
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            if (!tenant.getPlanId().equals(sub.getPlanId())
                    || (sub.getStatus() != B2BSubscription.B2BSubscriptionStatus.ACTIVE
                        && sub.getStatus() != B2BSubscription.B2BSubscriptionStatus.TRIAL)
                    || sub.getCurrentPeriodEnd().isBefore(today)
                    || (sub.getStatus() == B2BSubscription.B2BSubscriptionStatus.TRIAL
                        && sub.getTrialEndsAt() != null
                        && sub.getTrialEndsAt().isBefore(today))) {
                throw new AiPlanUnavailableException();
            }
        }

        PlatformPlan plan = planRepository.findById(tenant.getPlanId())
                .filter(PlatformPlan::isActive)
                .orElseThrow(AiPlanUnavailableException::new);
        PlanAiPolicy policy = policyRepository.findByPlanId(plan.getId())
                .orElseThrow(AiPlanUnavailableException::new);
        List<String> features = plan.getFeatures() == null
                ? List.of() : List.copyOf(plan.getFeatures());
        return new EffectiveAiPlan(plan.getCode(), features,
                policy.getDailyRequestLimit(), policy.getMonthlyTokenLimit(),
                policy.getTutorMessagesPerStudentDay(),
                policy.getInstitutionalDocumentLimit(),
                policy.getInstitutionalTextBytesLimit(),
                legacyWithoutSubscription);
    }

    @Transactional(readOnly = true)
    public EffectiveAiPlan require(String feature) {
        EffectiveAiPlan plan = current();
        if (!plan.features().contains(feature)) {
            throw new AiFeatureDisabledException(feature);
        }
        return plan;
    }

    public record EffectiveAiPlan(
            String planCode,
            List<String> features,
            int dailyRequestLimit,
            long monthlyTokenLimit,
            int tutorMessagesPerStudentDay,
            int institutionalDocumentLimit,
            long institutionalTextBytesLimit,
            boolean legacyWithoutSubscription
    ) {}
}
