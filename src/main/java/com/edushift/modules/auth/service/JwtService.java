package com.edushift.modules.auth.service;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.tenants.entity.Tenant;
import java.util.Set;

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
 */
public interface JwtService {

	/** Signed access token, ready to be sent in {@code Authorization: Bearer ...}. */
	String issueAccessToken(User user, Tenant tenant, Set<String> roles);

	/** Signed refresh token (opaque to clients; only {@code sub} + {@code tenant_id}). */
	String issueRefreshToken(User user, Tenant tenant);

	/** Parse + validate signature/expiration; returns the typed claims. */
	JwtClaims parseAndValidate(String token);

	/** Access token TTL in seconds (used to populate {@code expires_in}). */
	long accessTokenTtlSeconds();

	/** Token type discriminator carried in the {@code typ} private claim. */
	enum TokenType { ACCESS, REFRESH }

	/** Strongly-typed projection of the JWT claims we care about. */
	record JwtClaims(
			String subject,
			java.util.UUID tenantId,
			String tenantSlug,
			Set<String> roles,
			TokenType type,
			java.time.Instant issuedAt,
			java.time.Instant expiresAt
	) {}

}
