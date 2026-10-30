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
			FROM edushift.audit_logs audit
			WHERE (CAST(:tenantId AS uuid) IS NULL OR audit.tenant_id = CAST(:tenantId AS uuid))
			  AND (CAST(:actorId AS uuid) IS NULL OR audit.actor_id = CAST(:actorId AS uuid))
			  AND (CAST(:action AS varchar) IS NULL OR audit.action = CAST(:action AS varchar))
			  AND (CAST(:resourceType AS varchar) IS NULL OR audit.resource_type = CAST(:resourceType AS varchar))
			  AND (CAST(:tenantSearch AS varchar) IS NULL OR EXISTS (
			      SELECT 1 FROM edushift.tenants tenant
			      WHERE tenant.id = audit.tenant_id AND tenant.deleted = false
			        AND (tenant.name ILIKE '%' || CAST(:tenantSearch AS varchar) || '%'
			          OR tenant.slug ILIKE '%' || CAST(:tenantSearch AS varchar) || '%')
			  ))
			  AND (CAST(:actorSearch AS varchar) IS NULL OR EXISTS (
			      SELECT 1 FROM edushift.users actor
			      WHERE actor.public_uuid = audit.actor_id AND actor.deleted = false
			        AND (concat_ws(' ', actor.first_name, actor.last_name, actor.email)
			             ILIKE '%' || CAST(:actorSearch AS varchar) || '%')
			  ))
			  AND (CAST(:from AS timestamptz) IS NULL OR audit.occurred_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR audit.occurred_at <= CAST(:to AS timestamptz))
			ORDER BY audit.occurred_at DESC
			""",
			countQuery = """
			SELECT count(*)
			FROM edushift.audit_logs audit
			WHERE (CAST(:tenantId AS uuid) IS NULL OR audit.tenant_id = CAST(:tenantId AS uuid))
			  AND (CAST(:actorId AS uuid) IS NULL OR audit.actor_id = CAST(:actorId AS uuid))
			  AND (CAST(:action AS varchar) IS NULL OR audit.action = CAST(:action AS varchar))
			  AND (CAST(:resourceType AS varchar) IS NULL OR audit.resource_type = CAST(:resourceType AS varchar))
			  AND (CAST(:tenantSearch AS varchar) IS NULL OR EXISTS (
			      SELECT 1 FROM edushift.tenants tenant
			      WHERE tenant.id = audit.tenant_id AND tenant.deleted = false
			        AND (tenant.name ILIKE '%' || CAST(:tenantSearch AS varchar) || '%'
			          OR tenant.slug ILIKE '%' || CAST(:tenantSearch AS varchar) || '%')
			  ))
			  AND (CAST(:actorSearch AS varchar) IS NULL OR EXISTS (
			      SELECT 1 FROM edushift.users actor
			      WHERE actor.public_uuid = audit.actor_id AND actor.deleted = false
			        AND (concat_ws(' ', actor.first_name, actor.last_name, actor.email)
			             ILIKE '%' || CAST(:actorSearch AS varchar) || '%')
			  ))
			  AND (CAST(:from AS timestamptz) IS NULL OR audit.occurred_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR audit.occurred_at <= CAST(:to AS timestamptz))
			""",
			nativeQuery = true)
	Page<AuditLog> search(@Param("tenantId") UUID tenantId,
			@Param("actorId") UUID actorId,
			@Param("action") String action,
			@Param("resourceType") String resourceType,
			@Param("tenantSearch") String tenantSearch,
			@Param("actorSearch") String actorSearch,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable);

}
