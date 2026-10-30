package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.BillingPlanRule;
import java.util.List;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-filtered storage for automatic billing rules. */
public interface BillingPlanRuleRepository extends JpaRepository<BillingPlanRule, UUID> {
	List<BillingPlanRule> findAllByOrderByCreatedAtDesc();

	List<BillingPlanRule> findByActiveTrueOrderByCreatedAtDesc();

	Optional<BillingPlanRule> findByPublicUuid(UUID publicUuid);

	boolean existsByConceptIdAndLevelIdAndActiveTrue(UUID conceptId, UUID levelId);

	boolean existsByConceptIdAndGradeIdAndActiveTrue(UUID conceptId, UUID gradeId);
}
