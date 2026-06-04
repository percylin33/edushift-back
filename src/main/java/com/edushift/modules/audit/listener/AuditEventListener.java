package com.edushift.modules.audit.listener;

import com.edushift.modules.audit.entity.AuditLog;
import com.edushift.modules.audit.events.AuditEvent;
import com.edushift.modules.audit.repository.AuditLogRepository;
import com.edushift.shared.constants.LoggerNames;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists {@link AuditEvent} to {@code audit_logs} after the originating
 * transaction commits, on the {@code domainEventExecutor} pool, in a new
 * transaction (so audit writes never block business code).
 * <p>
 * Successful persistences are mirrored to the {@code edushift.audit} logger
 * (routed to its own retention-aware file in production). Failed writes are
 * logged through {@code edushift.exceptions} but never propagated to the caller.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

	private static final Logger audit = LoggerFactory.getLogger(LoggerNames.AUDIT);

	private static final Logger errors = LoggerFactory.getLogger(LoggerNames.EXCEPTIONS);

	private final AuditLogRepository auditLogRepository;

	@Async("domainEventExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void on(AuditEvent event) {
		try {
			AuditLog entry = new AuditLog();
			entry.setTenantId(event.getTenantId());
			entry.setActorId(event.getActorId());
			entry.setAction(event.getAction());
			entry.setResourceType(event.getResourceType());
			entry.setResourceId(event.getResourceId());
			entry.setSummary(event.getSummary());
			entry.setMetadata(event.getMetadata());
			entry.setTraceId(event.getTraceId());
			entry.setOccurredAt(event.getOccurredAt());
			auditLogRepository.save(entry);

			audit.info("audit_event eventId={} action={} resource={} resourceId={} actorId={} tenantId={} summary=\"{}\"",
					event.getEventId(), event.getAction(),
					event.getResourceType(), event.getResourceId(),
					event.getActorId(), event.getTenantId(),
					event.getSummary());
		}
		catch (Exception ex) {
			errors.error("audit_log_persist_failed eventId={} action={} resource={} resourceId={}",
					event.getEventId(), event.getAction(),
					event.getResourceType(), event.getResourceId(), ex);
		}
	}

}
