package com.edushift.modules.admin.tenants;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.admin.tenants.dto.AdminTenantAiSettingsResponse;
import com.edushift.modules.ai.exception.AiPlanUnavailableException;
import com.edushift.modules.ai.service.AiEntitlementService;
import com.edushift.modules.ai.service.AiEntitlementService.EffectiveAiPlan;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.identifier.UuidV7;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AdminTenantAiSettingsServiceImpl implements AdminTenantAiSettingsService {

	private static final String SETTINGS_QUERY = """
			SELECT ai_enabled, daily_request_quota, monthly_token_quota
			FROM edushift.tenant_ai_settings
			WHERE tenant_id = ? AND deleted = false
			""";

	private static final String UPSERT_SETTINGS = """
			INSERT INTO edushift.tenant_ai_settings (
				id, public_uuid, tenant_id, ai_enabled, daily_request_quota,
				monthly_token_quota, default_model, created_at, updated_at,
				created_by, updated_by, deleted
			) VALUES (?, ?, ?, ?, NULL, NULL, NULL, now(), now(), ?, ?, false)
			ON CONFLICT (tenant_id) DO UPDATE SET
				ai_enabled = EXCLUDED.ai_enabled,
				updated_at = now(),
				updated_by = EXCLUDED.updated_by,
				deleted = false
			""";

	private final TenantRepository tenantRepository;
	private final AiEntitlementService entitlementService;
	private final JdbcTemplate jdbcTemplate;
	private final AuditLogger auditLogger;
	private final PlatformTransactionManager transactionManager;

	@Override
	public AdminTenantAiSettingsResponse get(UUID tenantPublicUuid) {
		Tenant tenant = findTenant(tenantPublicUuid);
		PlanSnapshot plan = resolvePlan(tenant);
		return toResponse(readSettings(tenant.getPublicUuid()), plan);
	}

	@Override
	public AdminTenantAiSettingsResponse update(
			UUID tenantPublicUuid,
			boolean enabled,
			UUID actorUuid) {
		Tenant tenant = findTenant(tenantPublicUuid);
		PlanSnapshot plan = resolvePlan(tenant);
		if (enabled && !plan.available()) {
			throw new AiPlanUnavailableException();
		}

		SettingsSnapshot current = readSettings(tenant.getPublicUuid());
		SettingsSnapshot updated = new SettingsSnapshot(
				true, enabled,
				current.dailyRequestQuota(), current.monthlyTokenQuota());
		TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
				new TransactionTemplate(transactionManager).execute(status -> {
					jdbcTemplate.update(UPSERT_SETTINGS,
							UuidV7.create(), UUID.randomUUID(), tenant.getPublicUuid(), enabled,
							actorUuid, actorUuid);
					Map<String, Object> metadata = new HashMap<>();
					metadata.put("targetTenantPublicUuid", tenant.getPublicUuid().toString());
					metadata.put("enabled", enabled);
					metadata.put("planCode", plan.planCode());
					auditLogger.log(AuditAction.UPDATE, "tenant_ai_settings", tenant.getPublicUuid(),
							enabled ? "AI enabled for tenant" : "AI disabled for tenant", metadata);
					return null;
				}));
		return toResponse(updated, plan);
	}

	private Tenant findTenant(UUID tenantPublicUuid) {
		return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> tenantRepository.findByPublicUuid(tenantPublicUuid)
						.orElseThrow(() -> new ResourceNotFoundException(
								"tenants/" + tenantPublicUuid)));
	}

	private PlanSnapshot resolvePlan(Tenant tenant) {
		try {
			EffectiveAiPlan plan = TenantContext.runAs(tenant.getId(), entitlementService::current);
			boolean hasAssistantFeature = plan.features().contains(AiEntitlementService.TUTOR)
					|| plan.features().contains(AiEntitlementService.TEACHER)
					|| plan.features().contains(AiEntitlementService.FAMILY);
			boolean available = hasAssistantFeature
					&& plan.dailyRequestLimit() > 0
					&& plan.monthlyTokenLimit() > 0;
			return new PlanSnapshot(available, plan.planCode(),
					available ? plan.dailyRequestLimit() : 0,
					available ? plan.monthlyTokenLimit() : 0L,
					available ? null : "AI_PLAN_UNAVAILABLE");
		} catch (AiPlanUnavailableException exception) {
			String planCode = tenant.getPlan() == null ? null : tenant.getPlan().name();
			return new PlanSnapshot(false, planCode, 0, 0L, "AI_PLAN_UNAVAILABLE");
		}
	}

	private SettingsSnapshot readSettings(UUID tenantPublicUuid) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(SETTINGS_QUERY, tenantPublicUuid);
		if (rows.isEmpty()) {
			return new SettingsSnapshot(false, false, null, null);
		}
		Map<String, Object> row = rows.getFirst();
		return new SettingsSnapshot(
				true,
				Boolean.TRUE.equals(row.get("ai_enabled")),
				integerValue(row.get("daily_request_quota")),
				longValue(row.get("monthly_token_quota")));
	}

	private AdminTenantAiSettingsResponse toResponse(SettingsSnapshot settings, PlanSnapshot plan) {
		int dailyLimit = effectiveLimit(plan.dailyRequestLimit(), settings.dailyRequestQuota(), plan.available());
		long monthlyLimit = effectiveLimit(plan.monthlyTokenLimit(), settings.monthlyTokenQuota(), plan.available());
		String blockedReason = plan.blockedReason() != null ? plan.blockedReason()
				: !settings.enabled() ? "AI_DISABLED" : null;
		return new AdminTenantAiSettingsResponse(settings.configured(), settings.enabled(),
				plan.available(), plan.planCode(), dailyLimit, monthlyLimit, blockedReason);
	}

	private static int effectiveLimit(int planLimit, Integer tenantLimit, boolean planAvailable) {
		if (!planAvailable) return 0;
		return tenantLimit == null ? planLimit : Math.min(planLimit, tenantLimit);
	}

	private static long effectiveLimit(long planLimit, Long tenantLimit, boolean planAvailable) {
		if (!planAvailable) return 0L;
		return tenantLimit == null ? planLimit : Math.min(planLimit, tenantLimit);
	}

	private static Integer integerValue(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}

	private static Long longValue(Object value) {
		return value instanceof Number number ? number.longValue() : null;
	}

	private record PlanSnapshot(
			boolean available,
			String planCode,
			int dailyRequestLimit,
			long monthlyTokenLimit,
			String blockedReason) {}

	private record SettingsSnapshot(
			boolean configured,
			boolean enabled,
			Integer dailyRequestQuota,
			Long monthlyTokenQuota) {}
}
