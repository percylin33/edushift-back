package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/mfa/enroll/verify}.
 *
 * @param secretBase32 the base32-encoded TOTP secret returned by
 *                      {@code /mfa/enroll/start}; the FE echoes it back
 *                      so the server can validate the first code
 *                      before persisting.
 * @param totpCode     the 6-digit code from the user's authenticator app
 */
public record MfaEnrollVerifyRequest(
		@NotBlank(message = "secret is required")
		@Size(max = 64, message = "secret must not exceed 64 characters")
		String secret,

		@NotBlank(message = "totpCode is required")
		@Size(min = 6, max = 6, message = "totpCode must be exactly 6 digits")
		String totpCode
) {
}