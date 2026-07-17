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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

	@GetMapping
	@Operation(summary = "List tenants with optional filters (search / status / plan, paged)")
	public ApiResponse<Map<String, Object>> listAll(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) TenantStatus status,
			@RequestParam(required = false) TenantPlan plan,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : "";
		String statusName = status != null ? status.name() : "";
		String planName = plan != null ? plan.name() : "";
		int safeSize = Math.min(Math.max(size, 1), 100);
		int safePage = Math.max(page, 0);
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(safePage, safeSize);

		Map<String, Object> result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> {
					var pageResult = tenantRepository.adminSearch(
							trimmedSearch, statusName, planName, pageable);
					List<Tenant> tenants = pageResult.getContent();
					List<UUID> tenantIds = tenants.stream().map(Tenant::getId).toList();

					Aggregates aggregates = buildAggregates(tenantIds);

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
					Aggregates aggregates = buildAggregates(List.of(tenant.getId()));
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
					Aggregates aggregates = buildAggregates(List.of(saved.getId()));
					return AdminTenantMapper.toDetail(saved, aggregates);
				}));
		return ApiResponse.ok(result);
	}

	@PostMapping("/{uuid}/suspend")
	@Operation(summary = "Suspend a tenant")
	public ApiResponse<AdminTenantDetail> suspend(
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@PathVariable("uuid") UUID publicUuid) {
		AdminTenantDetail result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> new TransactionTemplate(txManager).execute(status -> {
					Tenant tenant = tenantRepository.findByPublicUuid(publicUuid)
							.orElseThrow(() -> new ResourceNotFoundException(
									"tenants/" + publicUuid));
					tenant.setStatus(TenantStatus.SUSPENDED);
					Tenant saved = tenantRepository.save(tenant);
					auditLogger.log(AuditAction.UPDATE, "tenant", publicUuid,
							"Tenant suspended",
							java.util.Map.of("actorUuid",
									actor != null ? actor.getId().toString() : null));
					Aggregates aggregates = buildAggregates(List.of(saved.getId()));
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
					Aggregates aggregates = buildAggregates(List.of(saved.getId()));
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
	 * <p>Sprint 16 / hardening — replaces the previous summary projection
	 * which left {@code planName}, {@code activeStudents} and
	 * {@code nextBillingDate} empty.</p>
	 */
	private Aggregates buildAggregates(List<UUID> tenantIds) {
		if (tenantIds == null || tenantIds.isEmpty()) {
			return new Aggregates(Map.of(), Map.of(), Map.of());
		}

		Map<UUID, PlatformPlan> plansById = new HashMap<>();
		platformPlanRepository.findAllById(
				tenantIds.stream()
						.map(this::lookupPlanIdForTenant)
						.filter(java.util.Objects::nonNull)
						.toList())
				.forEach(p -> plansById.put(p.getId(), p));

		Map<UUID, Long> activeStudentsByTenant = new HashMap<>();
		Map<UUID, B2BSubscription> activeSubscriptionByTenant = new HashMap<>();

		for (UUID tenantId : tenantIds) {
			activeStudentsByTenant.put(tenantId, studentRepository.countByTenantId(tenantId));
			b2bSubscriptionRepository
					.findByTenantIdAndStatusNot(tenantId, B2BSubscriptionStatus.CANCELED)
					.ifPresent(sub -> activeSubscriptionByTenant.put(tenantId, sub));
		}

		return new Aggregates(plansById, activeStudentsByTenant, activeSubscriptionByTenant);
	}

	/**
	 * Returns the {@code plan_id} for the given tenant, or {@code null}
	 * if the tenant has no plan assigned. Used to pre-resolve which
	 * {@link PlatformPlan}s to fetch in a single {@code findAllById}.
	 *
	 * <p>This intentionally re-queries {@link TenantRepository} instead
	 * of trusting caller-side state — the list call already loaded the
	 * tenants but the controller hands them to the mapper with their
	 * full state and the mapper never touches the repository again.</p>
	 */
	private UUID lookupPlanIdForTenant(UUID tenantId) {
		return tenantRepository.findById(tenantId).map(Tenant::getPlanId).orElse(null);
	}
}
