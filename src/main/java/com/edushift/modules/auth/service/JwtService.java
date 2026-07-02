package com.edushift.modules.auth.service;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.tenants.entity.Tenant;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and parses signed JSON Web Tokens for the auth module.
 *
 * <h3>Claim convention (access tokens)</h3>
 * <ul>
 *   <li>{@code sub}         — user {@code publicUuid} (UUIDv4 string)</li>
 *   <li>{@code tenant_id}   — tenant internal id (UUID string)</li>
 *   <li>{@code tenant_slug} — tenant slug (lowercase)</li>
 *   <li>{@code roles}       — array of role names (empty until role module ships)</li>
 *   <li>{@code email}       — user email (for client UI display only)</li>
 *   <li>{@code iat}, {@code exp}, {@code iss}, {@code aud} — RFC 7519 standard</li>
 * </ul>
 *
 * <h3>Refresh tokens</h3>
 * Refresh tokens are also JWTs in this implementation, but with:
 * <ul>
 *   <li>longer TTL (default 7d vs 15m)</li>
 *   <li>{@code typ=refresh} claim to prevent confusion with access tokens</li>
 *   <li>only {@code sub} and {@code tenant_id} (no roles, no email)</li>
 * </ul>
 * BE-1.4 will additionally persist a hash of each refresh token so they can
 * be revoked / rotated.
 *
 * <h3>Password-reset tokens (Sprint 17 / BE-17.1)</h3>
 * A third token kind is issued for the {@code /auth/forgot-password} flow:
 * <ul>
 *   <li>{@code typ=reset}</li>
 *   <li>{@code sub} = user {@code publicUuid}</li>
 *   <li>{@code tenant_id} / {@code tenant_slug} for cross-tenant protection</li>
 *   <li>short TTL (default 1h)</li>
 * </ul>
 * The token is also tracked in the {@code password_reset_tokens} table so we
 * can enforce single-use and supersede older tokens on a new request.
 */
public interface JwtService {

	/** Signed access token, ready to be sent in {@code Authorization: Bearer ...}. */
	String issueAccessToken(User user, Tenant tenant, Set<String> roles);

	/** Signed refresh token (opaque to clients; only {@code sub} + {@code tenant_id}). */
	String issueRefreshToken(User user, Tenant tenant);

	/**
	 * Signed password-reset token (Sprint 17 / BE-17.1).
	 *
	 * @param user      the user the reset is for
	 * @param tenant    the user's tenant
	 * @param jti       unique JWT id; persisted alongside the token in
	 *                  {@code password_reset_tokens} so we can enforce
	 *                  single-use and supersede older tokens
	 * @return the compact JWT string
	 */
	String issueResetToken(User user, Tenant tenant, UUID jti);

	/**
	 * Signed MFA challenge token (Sprint 17 / BE-17.2). Issued after a
	 * successful password check when the user has MFA enabled. The FE
	 * must present it as a bearer on {@code /auth/mfa/challenge}.
	 *
	 * <p>Claims mirror the access token (sub, tenant_id) so the
	 * challenge endpoint can resolve the user without a DB lookup on
	 * the JWT alone (the challenge still re-loads the user for the
	 * latest {@code mfaEnabled} state).
	 */
	String issueMfaToken(User user, Tenant tenant);

	/** Parse + validate signature/expiration; returns the typed claims. */
	JwtClaims parseAndValidate(String token);

	/** Access token TTL in seconds (used to populate {@code expires_in}). */
	long accessTokenTtlSeconds();

	/** Password-reset token TTL (used to populate {@code expiresAt} on the DB row). */
	java.time.Duration resetTokenTtl();

	/** MFA challenge token TTL in seconds. */
	long mfaTokenTtlSeconds();

	/** Token type discriminator carried in the {@code typ} private claim. */
	enum TokenType { ACCESS, REFRESH, RESET, MFA }

	/** Strongly-typed projection of the JWT claims we care about. */
	record JwtClaims(
			String subject,
			java.util.UUID tenantId,
			String tenantSlug,
			Set<String> roles,
			TokenType type,
			java.util.UUID jti,
			java.time.Instant issuedAt,
			java.time.Instant expiresAt
	) {}

}
