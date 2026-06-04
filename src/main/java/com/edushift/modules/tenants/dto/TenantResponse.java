package com.edushift.modules.tenants.dto;

import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Full tenant projection for authenticated callers.
 *
 * <h3>When to use this vs. {@link TenantSummary}</h3>
 * <ul>
 *   <li>{@link TenantSummary} — public; everything anyone could learn
 *       from the URL alone (branding to render the login screen).</li>
 *   <li>{@link TenantResponse} — authenticated; the admin's full view
 *       of "their" tenant: billing tier, trial window, capacity caps,
 *       feature flags, free-form settings. Returned by {@code GET
 *       /v1/tenants/me}.</li>
 * </ul>
 *
 * <h3>Settings & feature flags as raw maps</h3>
 * Both are intentionally typed as {@code Map<String, Object>}: they're
 * extension points whose contents evolve faster than the DTO surface
 * can accept. Strongly-typed flags would force an API release every
 * time we add an A/B switch. The keys are documented in
 * {@code docs/modules/tenants.md}; producers and consumers agree on
 * names there, not in compiled code.
 */
public record TenantResponse(
		UUID publicUuid,
		String name,
		String slug,
		String customDomain,
		TenantStatus status,
		TenantPlan plan,
		Instant trialEndsAt,
		BrandingDto branding,
		Map<String, Object> settings,
		Map<String, Object> featureFlags,
		Integer maxStudents,
		Integer maxTeachers,
		Instant createdAt,
		Instant updatedAt
) {
}
