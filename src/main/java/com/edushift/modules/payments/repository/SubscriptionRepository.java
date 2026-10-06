package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.Subscription;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

	Optional<Subscription> findByPublicUuid(UUID publicUuid);

	Page<Subscription> findByGuardianUserIdOrderByCreatedAtDesc(UUID guardianUserId, Pageable pageable);

	Page<Subscription> findAllByOrderByCreatedAtDesc(Pageable pageable);

	Page<Subscription> findByStatusOrderByCreatedAtDesc(Subscription.Status status, Pageable pageable);

	List<Subscription> findByStudentIdAndStatus(UUID studentId, Subscription.Status status);

	List<Subscription> findByStudentIdInAndStatus(Collection<UUID> studentIds, Subscription.Status status);

	boolean existsByStudentIdAndConceptIdAndStatus(UUID studentId, UUID conceptId, Subscription.Status status);

	/**
	 * Subscriptions due for billing right now (used by the cron).
	 */
	@Query("""
			SELECT s FROM Subscription s
			WHERE s.status = com.edushift.modules.payments.entity.Subscription$Status.ACTIVE
			  AND s.nextBillingAt IS NOT NULL
			  AND s.nextBillingAt <= :now
			ORDER BY s.nextBillingAt ASC
			""")
	List<Subscription> findDueForBilling(@Param("now") Instant now, Pageable pageable);
}
