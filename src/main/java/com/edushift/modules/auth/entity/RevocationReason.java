package com.edushift.modules.auth.entity;

/**
 * Why a {@link RefreshToken} was revoked. Persisted as {@code VARCHAR}; matches
 * the {@code chk_refresh_tokens_revoked_reason} CHECK constraint declared in
 * {@code V6}.
 */
public enum RevocationReason {

	/** Token was rotated by a successful {@code /auth/refresh}. Normal lifecycle. */
	ROTATED,

	/** User explicitly logged out via {@code /auth/logout}. */
	LOGOUT,

	/** A revoked token was presented again — the entire rotation chain is poisoned. */
	COMPROMISED,

	/** Background sweep marked tokens past {@code expires_at}. */
	EXPIRED,

	/** Manual admin action (e.g. password reset, account suspension, "log out everywhere"). */
	ADMIN_REVOKE;

}
