package com.edushift.modules.audit.controller;

import com.edushift.modules.audit.dto.AuditLogResponse;
import com.edushift.modules.audit.entity.AuditLog;
import com.edushift.modules.audit.mapper.AuditLogMapper;
import com.edushift.modules.audit.repository.AuditDirectoryRepository;
import com.edushift.modules.audit.repository.AuditLogRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 15 / F-07 / H-03: cross-tenant audit read API.
 *
 * <p>Restricted to {@code ROLE_SUPER_ADMIN} at the class level; no
 * per-method overrides — there is no tenant-scoped variant of this
 * endpoint today. If one is needed in the future, it should live in the
 * tenant-facing auth module, not here.</p>
 *
 * <p>Response shape follows the project standard {@link ApiResponse}
 * envelope with pagination metadata.</p>
 */
@RestController
@RequestMapping("/admin/audit")
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin Audit", description = "Cross-tenant audit log search (Sprint 15 / F-07)")
@RequiredArgsConstructor
public class AuditController {

	private static final int MAX_PAGE_SIZE = 200;

	private final AuditLogRepository auditLogRepository;
	private final AuditDirectoryRepository auditDirectoryRepository;
	private final TenantRepository tenantRepository;

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Search audit events (cross-tenant)")
	public ApiResponse<Page<AuditLogResponse>> search(
			@RequestParam(required = false) UUID tenantId,
			@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String tenantSearch,
			@RequestParam(required = false) String actorSearch,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {

		int pageIndex = page == null || page < 0 ? 0 : page;
		int pageSize = size == null || size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE);

		// Resolve labels in batches to avoid one query per row. This is a
		// cross-tenant directory lookup and must remain behind SUPER_ADMIN auth.
		var sourcePage = auditLogRepository.search(
				tenantId, actorId, action, resourceType,
				normalizeSearch(tenantSearch), normalizeSearch(actorSearch), from, to,
				PageRequest.of(pageIndex, pageSize));
		Set<UUID> actorIds = sourcePage.getContent().stream()
				.map(AuditLog::getActorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
		Set<UUID> tenantIds = sourcePage.getContent().stream()
				.map(AuditLog::getTenantId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
		Map<UUID, AuditDirectoryRepository.ActorLabel> actors = actorIds.isEmpty() ? Map.of()
				: auditDirectoryRepository.findActorLabels(actorIds).stream()
						.collect(Collectors.toMap(AuditDirectoryRepository.ActorLabel::getActorId, Function.identity()));
		Map<UUID, Tenant> tenants = tenantIds.isEmpty() ? Map.of()
				: tenantRepository.findAllById(tenantIds).stream()
						.collect(Collectors.toMap(Tenant::getId, Function.identity()));
		Page<AuditLogResponse> rows = sourcePage.map(log -> {
			var actor = log.getActorId() == null ? null : actors.get(log.getActorId());
			String fullName = actor == null ? null
					: ((actor.getFirstName() == null ? "" : actor.getFirstName().trim()) + " "
							+ (actor.getLastName() == null ? "" : actor.getLastName().trim())).trim();
			Tenant tenant = log.getTenantId() == null ? null : tenants.get(log.getTenantId());
			return AuditLogMapper.toResponse(log,
					fullName == null || fullName.isBlank() ? null : fullName,
					actor == null ? null : actor.getEmail(),
					tenant == null ? null : tenant.getName(),
					tenant == null ? null : tenant.getSlug());
		});

		return ApiResponse.ok(rows);
	}

	@GetMapping(value = "/export.csv", produces = "text/csv")
	@Operation(summary = "Export filtered audit events as CSV (capped at 5 000 rows)")
	public ResponseEntity<String> exportCsv(
			@RequestParam(required = false) UUID tenantId,
			@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String tenantSearch,
			@RequestParam(required = false) String actorSearch,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {

		Page<com.edushift.modules.audit.entity.AuditLog> rows = auditLogRepository.search(
				tenantId, actorId, action, null,
				normalizeSearch(tenantSearch), normalizeSearch(actorSearch), from, to,
				PageRequest.of(0, 5000));

		StringBuilder sb = new StringBuilder(64 * 1024);
		sb.append("id,occurred_at,tenant_id,actor_id,action,resource_type,resource_id,summary,trace_id\n");
		for (com.edushift.modules.audit.entity.AuditLog log : rows.getContent()) {
			sb.append(log.getId()).append(',')
					.append(log.getOccurredAt()).append(',')
					.append(log.getTenantId()).append(',')
					.append(log.getActorId()).append(',')
					.append(log.getAction() != null ? log.getAction().name() : "").append(',')
					.append(csvEscape(log.getResourceType())).append(',')
					.append(log.getResourceId()).append(',')
					.append(csvEscape(log.getSummary())).append(',')
					.append(csvEscape(log.getTraceId())).append('\n');
		}
		return ResponseEntity.ok()
				.header("Content-Disposition",
						"attachment; filename=\"audit-export.csv\"")
				.body(sb.toString());
	}

	private static String csvEscape(String value) {
		if (value == null) return "";
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String normalizeSearch(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
