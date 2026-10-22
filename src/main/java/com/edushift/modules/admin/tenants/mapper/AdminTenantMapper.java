package com.edushift.modules.admin.tenants.mapper;

import com.edushift.modules.admin.plans.PlatformPlan;
import com.edushift.modules.admin.subscriptions.B2BSubscription;
import com.edushift.modules.admin.tenants.dto.AdminTenantDetail;
import com.edushift.modules.admin.tenants.dto.AdminTenantDetail.SubscriptionSummary;
import com.edushift.modules.admin.tenants.dto.AdminTenantSummary;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantPlan;
import java.util.Map;
import java.util.List;
import java.time.Instant;

/**
 * Sprint 15 / F-05 / H-07 — entity → DTO mapper. Filters out any column
 * the FE has no business reading (internal settings, deleted audit fields).
 *
 * <p><b>Sprint 16 / hardening.</b> Cross-tenant aggregates (plan name,
 * student count, subscription summary) are NOT computed by the mapper
 * itself — the controller pre-resolves them via repository lookups and
 * passes them via the {@link Aggregates} record. This keeps the mapper
 * dependency-free (no repository injection) and lets callers batch the
 * lookups to avoid N+1.</p>
 */
public final class AdminTenantMapper {

	private AdminTenantMapper() {}

	/**
	 * Pre-resolved aggregates for the mapper. {@code null} keys (a tenant
	 * whose plan was deleted, for example) are tolerated by the mapper and
	 * surfaced as {@code null} fields on the DTO.
	 */
	public record Aggregates(
			Map<java.util.UUID, PlatformPlan> plansById,
			Map<java.util.UUID, Long> activeStudentsByTenant,
			Map<java.util.UUID, B2BSubscription> activeSubscriptionByTenant,
			Map<java.util.UUID, Long> usersByTenant,
			Map<java.util.UUID, Long> teachersByTenant,
			Map<java.util.UUID, Long> storageBytesByTenant,
			Instant metricsUpdatedAt
	) {}

	public static AdminTenantSummary toSummary(Tenant t, Aggregates agg) {
		if (t == null) return null;
		PlatformPlan plan = t.getPlanId() != null
				? agg.plansById().get(t.getPlanId())
				: null;
		B2BSubscription sub = agg.activeSubscriptionByTenant().get(t.getId());
		Long activeStudents = agg.activeStudentsByTenant().get(t.getId());
		return new AdminTenantSummary(
				t.getPublicUuid(),
				t.getSlug(),
				t.getName(),
				t.getStatus() != null ? t.getStatus().name() : null,
				t.getPlan() != null ? t.getPlan().name() : null,
				plan != null ? plan.getName() : legacyPlanDisplayName(t.getPlan()),
				activeStudents != null ? activeStudents.intValue() : null,
				t.getMaxStudents() != null ? t.getMaxStudents()
						: (plan != null ? plan.getMaxStudents() : null),
				sub != null ? sub.getNextBillingAt() : null,
				t.getTrialEndsAt(),
				t.getCreatedAt(),
				t.getUpdatedAt(),
				t.getBranding(),
				t.getFeatureFlags());
	}

	/**
	 * Fallback for tenants whose {@code plan_id} points at a deleted
	 * {@link PlatformPlan} row. We still want a friendly badge on the
	 * list — the legacy enum maps to the same codes used historically.
	 */
	private static String legacyPlanDisplayName(TenantPlan plan) {
		if (plan == null) return null;
		return switch (plan) {
			case TRIAL -> "Trial";
			case BASIC -> "Plan Básico";
			case PRO -> "Plan Pro";
			case ENTERPRISE -> "Enterprise";
		};
	}

	public static AdminTenantDetail toDetail(Tenant t, Aggregates agg) {
		if (t == null) return null;
		PlatformPlan plan = t.getPlanId() != null
				? agg.plansById().get(t.getPlanId())
				: null;
		B2BSubscription sub = agg.activeSubscriptionByTenant().get(t.getId());
		Long activeStudents = agg.activeStudentsByTenant().get(t.getId());
		Long totalUsers = agg.usersByTenant().get(t.getId());
		Long totalTeachers = agg.teachersByTenant().get(t.getId());
		Long storageUsedBytes = agg.storageBytesByTenant().get(t.getId());
		Integer maxStudents = t.getMaxStudents() != null
				? t.getMaxStudents() : (plan != null ? plan.getMaxStudents() : null);
		Integer maxTeachers = t.getMaxTeachers() != null
				? t.getMaxTeachers() : (plan != null ? plan.getMaxTeachers() : null);
		Integer maxStorageMb = plan != null ? plan.getMaxStorageMb() : null;

		return new AdminTenantDetail(
				t.getId(), t.getPublicUuid(),
				t.getSlug(), t.getName(), t.getCustomDomain(),
				t.getStatus() != null ? t.getStatus().name() : null,
				t.getPlan() != null ? t.getPlan().name() : null,
				plan != null ? plan.getName() : legacyPlanDisplayName(t.getPlan()),
				t.getPlanId(),
				activeStudents != null ? activeStudents.intValue() : null,
				totalUsers != null ? totalUsers.intValue() : 0,
				totalTeachers != null ? totalTeachers.intValue() : 0,
				t.getTrialEndsAt(),
				t.getBranding(),
				t.getFeatureFlags(),
				t.getSettings(),
				toSubscriptionSummary(sub, plan),
				maxStudents,
				maxTeachers,
				maxStorageMb,
				storageUsedBytes != null ? storageUsedBytes : 0L,
				plan != null && plan.getFeatures() != null ? List.copyOf(plan.getFeatures()) : List.of(),
				agg.metricsUpdatedAt(),
				t.getCreatedAt(),
				t.getUpdatedAt());
	}

	private static SubscriptionSummary toSubscriptionSummary(
			B2BSubscription sub, PlatformPlan plan) {
		if (sub == null) return null;
		String planName = plan != null ? plan.getName()
				: (sub.getPlanId() != null ? sub.getPlanId().toString() : null);
		return new SubscriptionSummary(
				sub.getStatus() != null ? sub.getStatus().name() : null,
				planName,
				sub.getCurrentPeriodStart(),
				sub.getCurrentPeriodEnd(),
				sub.getNextBillingAt(),
				sub.getTrialEndsAt(),
				sub.isCancelAtPeriodEnd(),
				sub.getCancellationReason());
	}
}
