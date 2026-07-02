package com.edushift.modules.auth.service;

import com.edushift.modules.auth.dto.AuthResponse;

/**
 * Orchestrates the Google Login flow on top of {@link AuthService}.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Verify the {@code id_token} via {@code GoogleIdentityProvider}
 *       (signature, audience, expiration, issuer — all offloaded to
 *       Google's library).</li>
 *   <li>Resolve the EduShift user (existing link or auto-provision).</li>
 *   <li>Stamp {@code last_login_at} and emit an EduShift JWT pair via
 *       {@link AuthService#issueSession} — the same path used by
 *       {@code /auth/login} and tenant self-signup.</li>
 *   <li>Audit both branches (success / failure) using the same
 *       {@link com.edushift.modules.audit.service.AuditLogger} that
 *       {@code AuthServiceImpl.login} uses.</li>
 * </ol>
 *
 * <h3>Why a separate service instead of adding to {@link AuthServiceImpl}</h3>
 * {@link AuthServiceImpl} is large (~540 lines) and tightly coupled to the
 * email/password flow. Adding a 100-line method would push it past 600
 * lines, dilute the BCrypt timing-attack decoy logic, and force the unit
 * tests for the password flow to mock {@link GoogleIdentityProvider}.
 * Splitting by provider keeps each service focused and lets us write
 * provider-specific tests without touching the other.
 *
 * <h3>Why this still depends on {@link AuthService}</h3>
 * The token-issuance + refresh-token persistence logic is non-trivial
 * (rotation lineage, SHA-256 hashing, audit). Reusing
 * {@link AuthService#issueSession} guarantees a single source of truth
 * for "what does it mean to start a session".
 */
public interface GoogleAuthService {

	/**
	 * Authenticates a user by a Google {@code id_token} issued for a
	 * known tenant and returns a fresh EduShift JWT pair.
	 *
	 * <p>Preconditions enforced by callers:
	 * <ul>
	 *   <li>Tenant resolution + status check has happened in the
	 *       controller (404 / 401 are surfaced before this method runs).</li>
	 *   <li>{@link com.edushift.shared.multitenancy.TenantContext} is bound
	 *       to the target tenant — this method runs inside
	 *       {@code TenantContext.runAs(...)} in the impl.</li>
	 * </ul>
	 *
	 * @param googleProfile already-verified profile from the id_token
	 * @param remoteAddr    IP for the audit log (best-effort; null is fine
	 *                      when behind a trusted proxy)
	 * @return the same {@link AuthResponse} envelope as {@code /auth/login}
	 */
	AuthResponse loginWithGoogle(
			com.edushift.infrastructure.integrations.google.GoogleProfile googleProfile,
			String tenantSlug,
			String remoteAddr);
}