package com.edushift.modules.admin.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned by {@code POST /admin/login} when the SUPER_ADMIN has not yet
 * enrolled MFA (Sprint 15 / F-02 / H-02).
 *
 * <p>The CLIENT must then:
 * <ol>
 *   <li>Call {@code POST /admin/mfa/enrol} with this token as bearer to
 *       receive a TOTP secret + QR code.</li>
 *   <li>Scan the QR with an authenticator app and POST the first 6-digit
 *       code to {@code POST /admin/mfa/verify-enrol} (same bearer).</li>
 *   <li>Receive a normal access/refresh pair back as
 *       {@link AdminLoginResponse}.</li>
 * </ol>
 *
 * <p>If the token expires the operator must restart the password login.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminMfaRequiredResponse(
		String onboardingToken,
		long expiresInSec,
		String tokenType,
		String reason
) implements com.edushift.modules.admin.auth.AdminAuthService.LoginOutcome {

	public static AdminMfaRequiredResponse onboarding(String token, long ttlSec) {
		return new AdminMfaRequiredResponse(token, ttlSec, "Bearer", "MFA_ENROLMENT_REQUIRED");
	}
}
