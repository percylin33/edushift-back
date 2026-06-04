package com.edushift.modules.tenants.entity;

/**
 * Lifecycle of a {@link Tenant} (school / institution).
 * <p>
 * Persisted as {@code VARCHAR} via {@code @Enumerated(STRING)}; matches the
 * {@code chk_tenants_status} CHECK constraint declared in {@code V4}.
 *
 * <h3>Transitions</h3>
 * <pre>
 *   PENDING   ──onboarding-complete──▶ ACTIVE
 *   ACTIVE    ──admin-suspend──▶       SUSPENDED
 *   ACTIVE    ──admin-disable──▶       INACTIVE
 *   SUSPENDED ──admin-restore──▶       ACTIVE
 *   INACTIVE  ──admin-restore──▶       ACTIVE
 * </pre>
 */
public enum TenantStatus {

	/** Tenant created but not yet onboarded; cannot authenticate users. */
	PENDING,

	/** Verified, fully usable tenant. Only ACTIVE tenants can authenticate. */
	ACTIVE,

	/** Temporarily blocked (billing, abuse, policy). Users cannot login. */
	SUSPENDED,

	/** Permanently disabled by an administrator. Users cannot login. */
	INACTIVE;

	/** True when users belonging to this tenant are allowed to authenticate. */
	public boolean canAuthenticate() {
		return this == ACTIVE;
	}

}
