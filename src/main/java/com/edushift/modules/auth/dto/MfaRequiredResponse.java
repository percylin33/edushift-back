package com.edushift.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned by {@code POST /auth/login} when the user has MFA enabled
 * (Sprint 17 / BE-17.2). The client must then call
 * {@code POST /auth/mfa/challenge} with the {@code mfaToken} as bearer
 * to complete the login.
 *
 * @param mfaToken   short-lived signed JWT (5 min TTL) that proves the
 *                   user passed the password check; required as the
 *                   {@code Authorization: Bearer ...} header on
 *                   {@code /mfa/challenge}
 * @param expiresInSec seconds until the mfaToken expires
 * @param tokenType  always {@code "Bearer"} (RFC 6750 compliance)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MfaRequiredResponse(
		String mfaToken,
		long expiresInSec,
		String tokenType
) {

	public static MfaRequiredResponse bearer(String mfaToken, long expiresInSec) {
		return new MfaRequiredResponse(mfaToken, expiresInSec, "Bearer");
	}
}