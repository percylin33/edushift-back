package com.edushift.modules.audit.repository;

import com.edushift.modules.audit.entity.AuditLog;
import com.edushift.modules.audit.events.AuditAction;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
