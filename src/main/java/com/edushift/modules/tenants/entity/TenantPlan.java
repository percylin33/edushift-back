package com.edushift.modules.tenants.entity;

/**
 * Billing tier of a {@link Tenant}.
 * <p>
 * Persisted as {@code VARCHAR} via {@code @Enumerated(STRING)}; matches the
 * {@code chk_tenants_plan} CHECK constraint declared in {@code V7}.
 *
 * <h3>Why an enum instead of a join to a {@code plans} table</h3>
 * The plan catalog evolves with the product, not with customer data —
 * adding {@code ENTERPRISE_PLUS} or splitting {@code PRO} requires a
 * deploy anyway (server-side limits, feature gates, billing webhooks).
 * Carrying the value in an enum keeps the surface small and lets us
 * pattern-match on it (default limits, feature flags) without an extra
 * lookup. The day plans become a true admin-managed catalog with custom
 * SKUs, we promote it to a table — until then, this is the right
 * weight.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   TRIAL      ──user-completes-payment──▶ BASIC | PRO | ENTERPRISE
 *   BASIC/PRO  ──admin-upgrade──▶          higher tier
 *   any        ──admin-downgrade──▶        BASIC (never back to TRIAL)
 * </pre>
 *
 * Only TRIAL exposes {@code trialEndsAt}; on upgrade we leave the
 * column populated as a historical "when did the trial end" but it
 * stops being functionally meaningful.
 */
public enum TenantPlan {

	/** 14-day evaluation window. New self-signups land here. */
	TRIAL,

	/** Entry tier for small institutions. */
	BASIC,

	/** Default tier for established schools. */
	PRO,

	/** Custom contracts: white-label, SSO, dedicated support. */
	ENTERPRISE

}
