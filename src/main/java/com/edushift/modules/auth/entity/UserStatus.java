package com.edushift.modules.auth.entity;

/**
 * Lifecycle state of an application {@link User}.
 * <p>
 * Persisted as {@code VARCHAR} via {@code @Enumerated(STRING)} — adding new
 * constants is safe; renaming or removing existing ones requires a data
 * migration.
 *
 * <h3>Allowed transitions (high level)</h3>
 * <pre>
 *   PENDING_VERIFICATION ──verify-email──▶ ACTIVE
 *   ACTIVE              ──admin-disable──▶ INACTIVE
 *   ACTIVE              ──admin-suspend──▶ SUSPENDED
 *   ACTIVE / INACTIVE   ──too-many-fails──▶ LOCKED
 *   INACTIVE / SUSPENDED / LOCKED ──admin-restore──▶ ACTIVE
 * </pre>
 */
public enum UserStatus {

	/** New account; awaits email confirmation before it can authenticate. */
	PENDING_VERIFICATION,

	/** Verified, fully usable account. */
	ACTIVE,

	/** Disabled by an administrator. Login is rejected. */
	INACTIVE,

	/** Temporarily blocked (policy / abuse). Login is rejected. */
	SUSPENDED,

	/** Automatically locked (e.g. too many failed login attempts). */
	LOCKED;

	/** True when the account can authenticate. */
	public boolean canAuthenticate() {
		return this == ACTIVE;
	}

	/** True when the account is in a terminal-blocked state and requires admin action. */
	public boolean isBlocked() {
		return this == INACTIVE || this == SUSPENDED || this == LOCKED;
	}

}
