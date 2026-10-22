package com.edushift.modules.admin.auth;

/** Short-lived proof returned after the password step for an MFA-enabled platform account. */
public record AdminMfaChallengeRequiredResponse(
		String mfaToken,
		long expiresInSec,
		String tokenType,
		String reason
) implements AdminAuthService.LoginOutcome {

	public static AdminMfaChallengeRequiredResponse bearer(String token, long expiresInSec) {
		return new AdminMfaChallengeRequiredResponse(token, expiresInSec, "Bearer", "MFA_REQUIRED");
	}
}
