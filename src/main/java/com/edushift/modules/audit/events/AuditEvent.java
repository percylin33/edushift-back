package com.edushift.modules.audit.events;

import com.edushift.shared.constants.ModuleNames;
import com.edushift.shared.event.AbstractDomainEvent;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Audit event emitted by any module to feed the activity timeline.
 * <p>
 * Published via {@link com.edushift.shared.event.DomainEventPublisher} and persisted
 * asynchronously by the audit listener after the originating transaction commits.
 * Fields not provided are filled from request context (actor, tenant, traceId).
 */
@Getter
public final class AuditEvent extends AbstractDomainEvent {

	/** Tenant scope (nullable for system-wide events). */
	private final UUID tenantId;

	/** Authenticated user that performed the action (nullable for system). */
	private final UUID actorId;

	private final AuditAction action;

	/** Logical type of the affected resource (e.g. "Student", "Tenant"). */
	private final String resourceType;

	/** Target resource id (nullable for non-resource actions like LOGIN). */
	private final UUID resourceId;

	/** Human-readable summary; localized at read time when needed. */
	private final String summary;

	/** Arbitrary structured payload (diffs, IP, user-agent, custom fields). */
	private final Map<String, Object> metadata;

	/** Correlation id (request/MDC) for log/audit cross-reference. */
	private final String traceId;

	@Builder
	public AuditEvent(
			String sourceModule,
			UUID tenantId,
			UUID actorId,
			AuditAction action,
			String resourceType,
			UUID resourceId,
			String summary,
			Map<String, Object> metadata,
			String traceId) {
		super(sourceModule != null ? sourceModule : ModuleNames.AUDIT);
		this.tenantId = tenantId;
		this.actorId = actorId;
		this.action = action;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.summary = summary;
		this.metadata = metadata;
		this.traceId = traceId;
	}

}
