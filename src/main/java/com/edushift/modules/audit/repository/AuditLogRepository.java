package com.edushift.modules.audit.repository;

import com.edushift.modules.audit.entity.AuditLog;
import com.edushift.modules.audit.events.AuditAction;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

	Page<AuditLog> findByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

	Page<AuditLog> findByTenantIdAndActorIdOrderByOccurredAtDesc(
			UUID tenantId, UUID actorId, Pageable pageable);

	Page<AuditLog> findByResourceTypeAndResourceIdOrderByOccurredAtDesc(
			String resourceType, UUID resourceId, Pageable pageable);

	Page<AuditLog> findByTenantIdAndActionAndOccurredAtBetweenOrderByOccurredAtDesc(
			UUID tenantId, AuditAction action, Instant from, Instant to, Pageable pageable);

	/**
	 * Sprint 15 / F-07 / H-03: native cross-tenant query for the SUPER_ADMIN
	 * audit console. Uses native SQL because JpaSpecificationExecutor with
	 * criteria across nullable predicates is heavier and harder to audit;
	 * the SQL surface is small and EXPLAIN-able.
	 *
	 * <p>All filters are optional. Tenant scoping is intentionally
	 * <strong>omitted</strong> — the SUPER_ADMIN consumes the result. Do
	 * NOT expose this query from any non-admin controller.</p>
	 */
	@Query(value = """
			SELECT *
			FROM edushift.audit_logs
			WHERE (:tenantId IS NULL OR tenant_id = :tenantId)
			  AND (:actorId   IS NULL OR actor_id   = :actorId)
			  AND (:action    IS NULL OR action     = :action)
			  AND (:resourceType IS NULL OR resource_type = :resourceType)
			  AND (:from      IS NULL OR occurred_at >= :from)
			  AND (:to        IS NULL OR occurred_at <= :to)
			ORDER BY occurred_at DESC
			""",
			countQuery = """
			SELECT count(*)
			FROM edushift.audit_logs
			WHERE (:tenantId IS NULL OR tenant_id = :tenantId)
			  AND (:actorId   IS NULL OR actor_id   = :actorId)
			  AND (:action    IS NULL OR action     = :action)
			  AND (:resourceType IS NULL OR resource_type = :resourceType)
			  AND (:from      IS NULL OR occurred_at >= :from)
			  AND (:to        IS NULL OR occurred_at <= :to)
			""",
			nativeQuery = true)
	Page<AuditLog> search(@Param("tenantId") UUID tenantId,
			@Param("actorId") UUID actorId,
			@Param("action") String action,
			@Param("resourceType") String resourceType,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

}
