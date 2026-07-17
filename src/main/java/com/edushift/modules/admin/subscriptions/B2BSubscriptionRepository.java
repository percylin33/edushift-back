package com.edushift.modules.admin.subscriptions;

import com.edushift.modules.admin.subscriptions.B2BSubscription.B2BSubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface B2BSubscriptionRepository extends JpaRepository<B2BSubscription, UUID> {

    Optional<B2BSubscription> findByTenantIdAndStatusNot(UUID tenantId, B2BSubscriptionStatus status);

    Optional<B2BSubscription> findByTenantId(UUID tenantId);

    List<B2BSubscription> findByStatusAndCurrentPeriodEndBefore(B2BSubscriptionStatus status, LocalDate date);

    List<B2BSubscription> findByStatus(B2BSubscriptionStatus status);

    List<B2BSubscription> findByPlanIdAndStatusIn(UUID planId, List<B2BSubscriptionStatus> statuses);
}
