package com.edushift.modules.payments.repository;

import com.edushift.modules.payments.entity.PaymentAccessOverride;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAccessOverrideRepository extends JpaRepository<PaymentAccessOverride, UUID> {

	Optional<PaymentAccessOverride> findByPublicUuid(UUID publicUuid);

	@Query("""
			SELECT o FROM PaymentAccessOverride o
			WHERE o.tenantId = :tenantId
			  AND o.untilAt > :now
			ORDER BY o.untilAt ASC
			""")
	List<PaymentAccessOverride> findActiveByTenant(
			@Param("tenantId") UUID tenantId,
			@Param("now") Instant now);

	@Query("""
			SELECT o FROM PaymentAccessOverride o
			WHERE o.publicUuid = :publicUuid
			  AND o.tenantId = :tenantId
			""")
	Optional<PaymentAccessOverride> findByPublicUuidAndTenantId(
			@Param("publicUuid") UUID publicUuid,
			@Param("tenantId") UUID tenantId);

	@Query("""
			SELECT o FROM PaymentAccessOverride o
			WHERE o.userPublicUuid = :userPublicUuid
			  AND o.untilAt > :now
			ORDER BY o.untilAt DESC
			""")
	List<PaymentAccessOverride> findActiveForUser(
			@Param("userPublicUuid") UUID userPublicUuid,
			@Param("now") Instant now);
}
