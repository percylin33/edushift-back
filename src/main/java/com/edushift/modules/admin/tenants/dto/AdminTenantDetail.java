package com.edushift.modules.admin.tenants.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.edushift.modules.tenants.entity.Tenant;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 15 / F-05 / H-07 — full tenant view (single-tenant GET).
 * Adds admin-only fields above {@link AdminTenantSummary}.
 *
 * <p><b>Sprint 16 / hardening.</b> The detail view now exposes the B2B
 * subscription summary (current period, next billing, cancellation flag),
 * tenant-wide counters (active students, total users, total teachers) and
 * the raw branding/feature-flag maps so the SUPER_ADMIN panel can render
 * operational data without a second round-trip.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminTenantDetail(
		UUID id,
		UUID publicUuid,
		String slug,
		String name,
		String customDomain,
		String status,
		String plan,
		String planName,
		UUID planId,
		Integer activeStudents,
		Integer totalUsers,
		Integer totalTeachers,
		Instant trialEndsAt,
		Map<String, Object> branding,
		Map<String, Object> featureFlags,
		Map<String, Object> settings,
		SubscriptionSummary subscription,
		Integer maxStudents,
		Integer maxTeachers,
		Integer maxStorageMb,
		Instant createdAt,
		Instant updatedAt
) {

	/**
	 * Light projection of {@code b2b_subscriptions} for the detail card.
	 * Sentinel-safe: every field is nullable so the FE can render
	 * "Sin suscripción activa" when the tenant has no row.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SubscriptionSummary(
			String status,
			String planName,
			LocalDate currentPeriodStart,
			LocalDate currentPeriodEnd,
			LocalDate nextBillingAt,
			LocalDate trialEndsAt,
			boolean cancelAtPeriodEnd,
			String cancellationReason
	) {}

	public static AdminTenantDetail from(Tenant t) {
		return new AdminTenantDetail(
				t.getId(), t.getPublicUuid(),
				t.getSlug(), t.getName(), t.getCustomDomain(),
				t.getStatus() != null ? t.getStatus().name() : null,
				t.getPlan() != null ? t.getPlan().name() : null,
				null,
				t.getPlanId(),
				null, null, null,
				t.getTrialEndsAt(),
				t.getBranding(),
				t.getFeatureFlags(),
				t.getSettings(),
				null,
				t.getMaxStudents(),
				t.getMaxTeachers(),
				null,
				t.getCreatedAt(),
				t.getUpdatedAt());
	}
}
