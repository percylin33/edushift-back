package com.edushift.modules.paymentmethods.repository;

import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod;
import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantPaymentMethodRepository extends JpaRepository<TenantPaymentMethod, UUID> {

	@Query("SELECT m FROM TenantPaymentMethod m WHERE m.tenantId = :tenantId AND m.deleted = false ORDER BY m.createdAt DESC")
	List<TenantPaymentMethod> findActiveByTenant(@Param("tenantId") UUID tenantId);

	@Query("SELECT m FROM TenantPaymentMethod m WHERE m.tenantId = :tenantId AND m.publicUuid = :publicUuid AND m.deleted = false")
	Optional<TenantPaymentMethod> findActiveByPublicUuid(@Param("tenantId") UUID tenantId, @Param("publicUuid") UUID publicUuid);

	@Query("SELECT m FROM TenantPaymentMethod m WHERE m.tenantId = :tenantId AND m.defaultMethod = true AND m.deleted = false")
	Optional<TenantPaymentMethod> findDefaultByTenant(@Param("tenantId") UUID tenantId);

	@Query("SELECT COUNT(m) FROM TenantPaymentMethod m WHERE m.tenantId = :tenantId AND m.deleted = false")
	long countActiveByTenant(@Param("tenantId") UUID tenantId);

	@Query("SELECT m FROM TenantPaymentMethod m WHERE m.tenantId = :tenantId AND m.methodType = :type AND m.deleted = false")
	List<TenantPaymentMethod> findActiveByTenantAndType(@Param("tenantId") UUID tenantId, @Param("type") PaymentMethodType type);
}