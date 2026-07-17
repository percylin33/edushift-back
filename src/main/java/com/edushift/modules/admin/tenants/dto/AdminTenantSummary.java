package com.edushift.modules.admin.tenants.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 15 / F-05 / H-07 — server-side projection of {@code tenants} for
 * the admin console list view.
 *
 * <p>Sensitive internal columns ({@code settings}, internal-only flags)
 * are excluded; branding + feature flags are exposed as opaque maps since
 * the FE already understands them via the regular tenant API.</p>
 *
 * <p><b>Sprint 16 / hardening.</b> Three cross-tenant aggregates are
 * pre-computed at the controller layer to avoid N+1 queries:</p>
 * <ul>
 *   <li>{@link #planName} — human-readable name from {@code platform_plans}.</li>
 *   <li>{@link #activeStudents} — count of {@code ACTIVE} students in the tenant.</li>
 *   <li>{@link #nextBillingDate} — from {@code b2b_subscriptions.next_billing_at}
 *       when the tenant has an active subscription; {@code null} otherwise.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminTenantSummary(
		UUID publicUuid,
		String slug,
		String name,
		String status,
		String plan,
		String planName,
		Integer activeStudents,
		LocalDate nextBillingDate,
		Instant trialEndsAt,
		Instant createdAt,
		Instant updatedAt,
		Map<String, Object> branding,
		Map<String, Object> featureFlags
) {}
