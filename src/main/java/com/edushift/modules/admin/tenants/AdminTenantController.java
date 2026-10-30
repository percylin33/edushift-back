package com.edushift.modules.admin.tenants;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.plans.PlatformPlanRepository;
import com.edushift.modules.admin.subscriptions.B2BSubscription;
import com.edushift.modules.admin.subscriptions.B2BSubscription.B2BSubscriptionStatus;
import com.edushift.modules.admin.subscriptions.B2BSubscriptionRepository;
import com.edushift.modules.admin.tenants.dto.AdminTenantDetail;
import com.edushift.modules.admin.tenants.dto.AdminTenantSummary;
import com.edushift.modules.admin.tenants.mapper.AdminTenantMapper;
import com.edushift.modules.admin.tenants.mapper.AdminTenantMapper.Aggregates;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants")
@Validated
@Tag(name = "Admin Tenants", description = "Tenant management (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTenantController {

	private final TenantRepository tenantRepository;
	private final PlatformPlanRepository platformPlanRepository;
	private final B2BSubscriptionRepository b2bSubscriptionRepository;
	private final StudentRepository studentRepository;
	private final AuditLogger auditLogger;
	private final PlatformTransactionManager txManager;
	private final JdbcTemplate jdbcTemplate;

	@GetMapping
	@Operation(summary = "List tenants with optional filters (search / status / plan, paged)")
	public ApiResponse<Map<String, Object>> listAll(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) TenantStatus status,
			@RequestParam(required = false) TenantPlan plan,
			@RequestParam(defaultValue = "recent") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : "";
		String statusName = status != null ? status.name() : "";
		String planName = plan != null ? plan.name() : "";
		String safeSort = switch (sort == null ? "" : sort.toLowerCase(java.util.Locale.ROOT)) {
			case "students" -> "students";
			case "billing" -> "billing";
			case "name" -> "name";
			default -> "recent";
		};
		int safeSize = Math.min(Math.max(size, 1), 100);
		int safePage = Math.max(page, 0);
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(safePage, safeSize);

		Map<String, Object> result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> {
					var pageResult = tenantRepository.adminSearch(
							trimmedSearch, statusName, planName, safeSort, pageable);
					List<Tenant> tenants = pageResult.getContent();
					Aggregates aggregates = buildAggregates(tenants);

					List<AdminTenantSummary> rows = tenants.stream()
							.map(t -> AdminTenantMapper.toSummary(t, aggregates))
							.toList();
					Map<String, Object> body = new java.util.LinkedHashMap<>();
					body.put("content", rows);
					body.put("totalElements", pageResult.getTotalElements());
					body.put("totalPages", pageResult.getTotalPages());
					body.put("number", pageResult.getNumber());
					body.put("size", pageResult.getSize());
					return body;
				});
		return ApiResponse.ok(result);
	}

	@GetMapping("/{uuid}")
	@Operation(summary = "Get tenant detail")
	public ApiResponse<AdminTenantDetail> get(@PathVariable("uuid") UUID publicUuid) {
		AdminTenantDetail result = TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> {
					Tenant tenant = tenantRepository.findByPublicUuid(publicUuid)
							.orElseThrow(() -> new ResourceNotFoundException(
									"tenants/" + publicUuid));
					Aggregates aggregates = buildAggregates(List.of(tenant));
					return AdminTenantMapper.toDetail(tenant, aggregates);
				});
		return ApiResponse.ok(result);
	}

	@PatchMapping("/{uuid}")
	@Operation(summary = "Update tenant (status, branding, settings)")
	public ApiResponse<AdminTenantDetail> update(
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@PathVariable("uuid") UUID publicUuid,
			@RequestBody TenantUpdateRequest request) {
		AdminTenantDetail result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> new TransactionTemplate(txManager).execute(status -> {
					Tenant tenant = tenantRepository.findByPublicUuid(publicUuid)
							.orElseThrow(() -> new ResourceNotFoundException(
									"tenants/" + publicUuid));
					if (request.name() != null) tenant.setName(request.name());
					if (request.status() != null) tenant.setStatus(request.status());
					if (request.planId() != null) tenant.setPlanId(request.planId());
					Tenant saved = tenantRepository.save(tenant);
					auditLogger.log(AuditAction.UPDATE, "tenant", publicUuid,
							"Tenant updated",
							java.util.Map.of(
									"name", String.valueOf(request.name()),
									"status", String.valueOf(request.status()),
									"planId", String.valueOf(request.planId()),
									"actorUuid",
									actor != null ? actor.getId().toString() : null));
					Aggregates aggregates = buildAggregates(List.of(saved));
					return AdminTenantMapper.toDetail(saved, aggregates);
				}));
		return ApiResponse.ok(result);
	}

	@PostMapping("/{uuid}/suspend")
	@Operation(summary = "Suspend a tenant")
	public ApiResponse<AdminTenantDetail> suspend(
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@PathVariable("uuid") UUID publicUuid,
			@Valid @org.springframework.web.bind.annotation.RequestBody TenantSuspendRequest request) {
		AdminTenantDetail result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> new TransactionTemplate(txManager).execute(status -> {
					Tenant tenant = tenantRepository.findByPublicUuid(publicUuid)
							.orElseThrow(() -> new ResourceNotFoundException(
									"tenants/" + publicUuid));
					tenant.setStatus(TenantStatus.SUSPENDED);
					Tenant saved = tenantRepository.save(tenant);
					auditLogger.log(AuditAction.UPDATE, "tenant", publicUuid,
							"Tenant suspended: " + request.reason().trim(),
							java.util.Map.of("actorUuid",
									actor != null ? actor.getId().toString() : null));
					Aggregates aggregates = buildAggregates(List.of(saved));
					return AdminTenantMapper.toDetail(saved, aggregates);
				}));
		return ApiResponse.ok(result);
	}

	@PostMapping("/{uuid}/reactivate")
	@Operation(summary = "Reactivate a tenant")
	public ApiResponse<AdminTenantDetail> reactivate(
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@PathVariable("uuid") UUID publicUuid) {
		AdminTenantDetail result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> new TransactionTemplate(txManager).execute(status -> {
					Tenant tenant = tenantRepository.findByPublicUuid(publicUuid)
							.orElseThrow(() -> new ResourceNotFoundException(
									"tenants/" + publicUuid));
					tenant.setStatus(TenantStatus.ACTIVE);
					Tenant saved = tenantRepository.save(tenant);
					auditLogger.log(AuditAction.UPDATE, "tenant", publicUuid,
							"Tenant reactivated",
							java.util.Map.of("actorUuid",
									actor != null ? actor.getId().toString() : null));
					Aggregates aggregates = buildAggregates(List.of(saved));
					return AdminTenantMapper.toDetail(saved, aggregates);
				}));
		return ApiResponse.ok(result);
	}

	public record TenantUpdateRequest(
			@Size(max = 200) String name,
			TenantStatus status,
			UUID planId
	) {}

	/**
	 * Batch-load cross-tenant aggregates for the listed tenants so the
	 * mapper never has to query on its own (no N+1). Each lookup uses an
	 * explicit {@code tenant_id} predicate because the call is made
	 * under {@code SUPER_ADMIN_SENTINEL} which bypasses Hibernate's
	 * {@code @TenantId} auto-filter.
	 *
	 * <p>All list aggregates are fetched in grouped queries, avoiding one query
	 * per tenant. Capacity is resolved from the plan or tenant override.</p>
	 */
	private Aggregates buildAggregates(List<Tenant> tenants) {
		if (tenants == null || tenants.isEmpty()) {
			return new Aggregates(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Instant.now());
		}
		List<UUID> tenantIds = tenants.stream().map(Tenant::getId).toList();
		String tenantPlaceholders = String.join(", ", java.util.Collections.nCopies(tenantIds.size(), "?"));

		Map<UUID, PlatformPlan> plansById = new HashMap<>();
		platformPlanRepository.findAllById(
				tenants.stream()
						.map(Tenant::getPlanId)
						.filter(java.util.Objects::nonNull)
						.distinct()
						.toList())
				.forEach(p -> plansById.put(p.getId(), p));

		Map<UUID, Long> activeStudentsByTenant = new HashMap<>();
		Map<UUID, Long> usersByTenant = new HashMap<>();
		Map<UUID, Long> teachersByTenant = new HashMap<>();
		Map<UUID, Long> storageBytesByTenant = new HashMap<>();
		Map<UUID, B2BSubscription> activeSubscriptionByTenant = new HashMap<>();
		tenantIds.forEach(tenantId -> activeStudentsByTenant.put(tenantId, 0L));
		tenantIds.forEach(tenantId -> usersByTenant.put(tenantId, 0L));
		tenantIds.forEach(tenantId -> teachersByTenant.put(tenantId, 0L));
		tenantIds.forEach(tenantId -> storageBytesByTenant.put(tenantId, 0L));
		studentRepository.countEnrolledStudentsByTenantIds(tenantIds, EnrollmentStatus.ENROLLED)
				.forEach(row -> activeStudentsByTenant.put(row.getTenantId(), row.getStudentCount()));
		jdbcTemplate.query(
				"SELECT tenant_id, COUNT(*) AS total_users, "
						+ "COUNT(*) FILTER (WHERE 'TEACHER' = ANY(roles)) AS total_teachers "
						+ "FROM edushift.users WHERE tenant_id IN (" + tenantPlaceholders + ") "
						+ "AND status = 'ACTIVE' AND deleted = false GROUP BY tenant_id",
						(rs, rowNum) -> {
							UUID tenantId = (UUID) rs.getObject("tenant_id");
							usersByTenant.put(tenantId, rs.getLong("total_users"));
							teachersByTenant.put(tenantId, rs.getLong("total_teachers"));
							return tenantId;
						}, tenantIds.toArray());
		jdbcTemplate.query(
				"SELECT tenant_id, COALESCE(SUM(size_bytes), 0) AS storage_bytes "
						+ "FROM edushift.lms_file_objects WHERE tenant_id IN (" + tenantPlaceholders + ") "
						+ "AND deleted = false GROUP BY tenant_id",
						(rs, rowNum) -> {
							UUID tenantId = (UUID) rs.getObject("tenant_id");
							storageBytesByTenant.put(tenantId, rs.getLong("storage_bytes"));
							return tenantId;
						}, tenantIds.toArray());
		b2bSubscriptionRepository.findAllByTenantIdInAndStatusNot(
				tenantIds, B2BSubscriptionStatus.CANCELED)
			.forEach(sub -> activeSubscriptionByTenant.putIfAbsent(sub.getTenantId(), sub));

		return new Aggregates(plansById, activeStudentsByTenant, activeSubscriptionByTenant,
				usersByTenant, teachersByTenant, storageBytesByTenant, Instant.now());
	}

	public record TenantSuspendRequest(@NotBlank @Size(max = 500) String reason) {}
}
