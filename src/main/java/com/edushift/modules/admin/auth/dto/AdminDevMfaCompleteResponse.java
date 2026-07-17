package com.edushift.modules.admin.auth.dto;

import com.edushift.modules.admin.auth.AdminAuthService;
import com.edushift.modules.admin.auth.AdminLoginResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Sprint 15 / dev-only MFA enrolment bypass response.
 *
 * <p>Carries the {@code session} (a regular {@link AdminLoginResponse}
 * payload) plus a {@code bootstrap} field with the freshly generated
 * TOTP secret + otpauth URI + recovery codes so the operator can import
 * the credentials into a real authenticator app later and continue
 * exercising the production flow.</p>
 *
 * <p>This DTO is intentionally NOT shipped in prod builds — the
 * controller that returns it is bean-gated by
 * {@code @Profile({"dev","local"})}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminDevMfaCompleteResponse(
		AdminLoginResponse session,
		Bootstrap bootstrap
) implements AdminAuthService.LoginOutcome {

	/**
	 * Operator-facing bootstrap material. {@code totpSecret} is the
	 * raw base32 secret that can be pasted into Google Authenticator's
	 * "Enter setup key" flow; {@code otpauthUri} can be encoded into a
	 * QR for a phone. {@code recoveryCodes} are shown ONCE here — the
	 * controller is the only place they ever appear in plaintext.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Bootstrap(
			String totpSecret,
			String otpauthUri,
			List<String> recoveryCodes
	) {}
}
