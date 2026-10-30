package com.edushift.modules.admin.plans;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanAiPolicyRepository extends JpaRepository<PlanAiPolicy, UUID> {
    Optional<PlanAiPolicy> findByPlanId(UUID planId);
}
