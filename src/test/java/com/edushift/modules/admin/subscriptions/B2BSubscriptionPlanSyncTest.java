package com.edushift.modules.admin.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class B2BSubscriptionPlanSyncTest {
    @Test
    void assigningPlanUpdatesTenantInSameServiceOperation() {
        UUID tenantPublicUuid = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID previousPlanId = UUID.randomUUID();
        var subscriptionRepository = mock(B2BSubscriptionRepository.class);
        var planRepository = mock(PlatformPlanRepository.class);
        var tenantRepository = mock(TenantRepository.class);
        var service = new B2BSubscriptionService(
                subscriptionRepository, planRepository, tenantRepository);

        PlatformPlan plan = new PlatformPlan();
        plan.setId(planId);
        plan.setActive(true);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setPublicUuid(tenantPublicUuid);
        tenant.setPlanId(previousPlanId);
        when(tenantRepository.findByPublicUuid(tenantPublicUuid)).thenReturn(Optional.of(tenant));
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(B2BSubscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.assignOrChangePlan(tenantPublicUuid, new B2BSubscriptionRequest(planId, null, null));

        assertThat(tenant.getPlanId()).isEqualTo(planId);
        verify(tenantRepository).save(tenant);
    }
}
