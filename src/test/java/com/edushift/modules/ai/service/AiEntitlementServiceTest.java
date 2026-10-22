package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiEntitlementServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final B2BSubscriptionRepository subscriptionRepository =
            mock(B2BSubscriptionRepository.class);
    private final PlatformPlanRepository planRepository = mock(PlatformPlanRepository.class);
    private final PlanAiPolicyRepository policyRepository = mock(PlanAiPolicyRepository.class);
    private final AiEntitlementService service = new AiEntitlementService(
            tenantRepository, subscriptionRepository, planRepository, policyRepository);

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setPlanId(PLAN_ID);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        PlatformPlan plan = new PlatformPlan();
        plan.setId(PLAN_ID);
        plan.setCode("PRO");
        plan.setActive(true);
        plan.setFeatures(List.of(AiEntitlementService.GENERATE, AiEntitlementService.TUTOR));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        PlanAiPolicy policy = new PlanAiPolicy();
        policy.setPlanId(PLAN_ID);
        policy.setDailyRequestLimit(200);
        policy.setMonthlyTokenLimit(1_000_000);
        when(policyRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(policy));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsEntitlementsForLegacyTenantPlan() {
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        var result = service.require(AiEntitlementService.TUTOR);
        assertThat(result.planCode()).isEqualTo("PRO");
        assertThat(result.dailyRequestLimit()).isEqualTo(200);
        assertThat(result.legacyWithoutSubscription()).isTrue();
    }

    @Test
    void deniesFeatureNotIncludedInPlan() {
        assertThatThrownBy(() -> service.require(AiEntitlementService.FAMILY))
                .isInstanceOf(AiFeatureDisabledException.class);
    }

    @Test
    void rejectsSubscriptionWithDifferentPlan() {
        B2BSubscription subscription = activeSubscription();
        subscription.setPlanId(UUID.randomUUID());
        when(subscriptionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(subscription));
        assertThatThrownBy(service::current)
                .isInstanceOf(AiPlanUnavailableException.class);
    }

    @Test
    void rejectsPastDueSubscription() {
        B2BSubscription subscription = activeSubscription();
        subscription.setStatus(B2BSubscription.B2BSubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(subscription));
        assertThatThrownBy(service::current)
                .isInstanceOf(AiPlanUnavailableException.class);
    }

    @Test
    void acceptsActiveMatchingSubscription() {
        when(subscriptionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(activeSubscription()));
        assertThat(service.current().legacyWithoutSubscription()).isFalse();
    }

    private static B2BSubscription activeSubscription() {
        B2BSubscription subscription = new B2BSubscription();
        subscription.setTenantId(TENANT_ID);
        subscription.setPlanId(PLAN_ID);
        subscription.setStatus(B2BSubscription.B2BSubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(LocalDate.now(ZoneOffset.UTC).plusDays(1));
        return subscription;
    }
}
