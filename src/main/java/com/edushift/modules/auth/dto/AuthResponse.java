package com.edushift.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Successful authentication payload returned by {@code POST /auth/login} and
 * {@code POST /auth/refresh}.
 *
 * @param accessToken   short-lived signed JWT (default 15 min) for API calls
 * @param refreshToken  long-lived opaque token (default 7 days) used to mint
 *                      new access tokens; rotated on each {@code /refresh}
 * @param tokenType     always {@code "Bearer"} (RFC 6750 compliance)
 * @param expiresInSec  access token TTL in seconds; clients use this to refresh
 *                      proactively before expiration
 * @param user          minimal projection of the authenticated user (so
 *                      the frontend can render the shell without an extra
 *                      {@code GET /me} round-trip)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSec,
		UserSummary user
) {

	/** Convenience constructor that hard-codes the standard {@code Bearer} prefix. */
	public static AuthResponse bearer(String accessToken, String refreshToken,
	                                   long expiresInSec, UserSummary user) {
		return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSec, user);
	}

}
