package com.edushift.modules.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 15 / F-07 / H-03: server-side projection of {@code audit_logs} for
 * the SUPER_ADMIN audit console. Sensitive columns (raw SQL, password
 * hashes, tokens) are explicitly excluded — the contract is additive only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
		UUID id,
		Instant occurredAt,
		UUID tenantId,
		UUID actorId,
		String action,
		String resourceType,
		UUID resourceId,
		String summary,
		Map<String, Object> metadata,
		String traceId
) {}
