package com.edushift.modules.audit.service;

import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.events.AuditEvent;
import com.edushift.shared.constants.ModuleNames;
import com.edushift.shared.event.DomainEventPublisher;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Public façade for emitting audit events from any module.
 * <p>
 * Fills missing actor/tenant/traceId from the current request context, then
 * publishes an {@link AuditEvent}; the audit listener persists it asynchronously
 * after the originating transaction commits.
 */
@Service
@RequiredArgsConstructor
public class AuditLogger {

	private static final String MDC_TRACE_ID = "traceId";

	private final DomainEventPublisher publisher;

	private final CurrentUserProvider currentUserProvider;

	public void log(AuditAction action, String resourceType, UUID resourceId, String summary) {
		log(action, resourceType, resourceId, summary, Map.of(), null);
	}

	public void log(
			AuditAction action,
			String resourceType,
			UUID resourceId,
			String summary,
			Map<String, Object> metadata) {
		log(action, resourceType, resourceId, summary, metadata, null);
	}

	public void log(
			AuditAction action,
			String resourceType,
			UUID resourceId,
			String summary,
			Map<String, Object> metadata,
			String sourceModule) {

		AuditEvent event = AuditEvent.builder()
				.sourceModule(sourceModule != null ? sourceModule : ModuleNames.AUDIT)
				.tenantId(currentUserProvider.currentTenantId().orElse(null))
				.actorId(currentUserProvider.currentUserId().orElse(null))
				.action(action)
				.resourceType(resourceType)
				.resourceId(resourceId)
				.summary(summary)
				.metadata(metadata)
				.traceId(MDC.get(MDC_TRACE_ID))
				.build();

		publisher.publish(event);
	}

}
