package com.edushift.modules.audit.mapper;

import com.edushift.modules.audit.dto.AuditLogResponse;
import com.edushift.modules.audit.entity.AuditLog;

/**
 * Sprint 15 / F-07 / H-03: entity → DTO mapper. Intentionally narrow —
 * does NOT expose the audit {@code id} (internal UUIDv7) as anything other
 * than the primary key for client pagination; everything else mirrors
 * the entity surface minus the persistable timestamps which we re-use as
 * the wire-format {@code occurredAt}.
 */
public final class AuditLogMapper {

	private AuditLogMapper() {}

	public static AuditLogResponse toResponse(AuditLog log) {
		if (log == null) {
			return null;
		}
		return new AuditLogResponse(
				log.getId(),
				log.getOccurredAt(),
				log.getTenantId(),
				log.getActorId(),
				log.getAction() != null ? log.getAction().name() : null,
				log.getResourceType(),
				log.getResourceId(),
				log.getSummary(),
				log.getMetadata(),
				log.getTraceId());
	}
}
