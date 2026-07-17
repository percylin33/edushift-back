package com.edushift.modules.admin.plans;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformPlanRepository extends JpaRepository<PlatformPlan, UUID> {

    Optional<PlatformPlan> findByCode(String code);

    List<PlatformPlan> findByIsActiveTrueOrderBySortOrder();

    boolean existsByCodeAndIsActiveTrue(String code);
}
