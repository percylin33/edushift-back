package com.edushift.modules.audit.dto;

import com.edushift.modules.audit.events.AuditAction;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 15 / F-07 / H-03: query parameters for
 * {@code GET /admin/audit}. Every filter is optional; omitting them
 * returns the most recent entries first (server-default ordering).
 *
 * @param tenantId      restrict to a single tenant
 * @param actorId       restrict to a single actor (USER publicUuid)
 * @param action        enum name; case-insensitive matching is done at
 *                      the controller layer
 * @param resourceType  logical type of the affected resource
 * @param from          inclusive lower bound on {@code occurred_at}
 * @param to            inclusive upper bound on {@code occurred_at}
 * @param traceId       exact correlation id match
 * @param page          0-based page index
 * @param size          page size (capped server-side at 200)
 */
public record AuditLogSearchRequest(
		UUID tenantId,
		UUID actorId,
		AuditAction action,
		@Size(max = 100) String resourceType,
		Instant from,
		Instant to,
		@Size(max = 64) String traceId,
		Integer page,
		Integer size
) {
	public int pageOrDefault() { return page == null || page < 0 ? 0 : page; }
	public int sizeOrDefault() {
		if (size == null || size <= 0) return 50;
		return Math.min(size, 200);
	}
}
