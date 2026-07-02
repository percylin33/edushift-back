package com.edushift.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Returned by the MFA endpoints ({@code /mfa/enroll/start},
 * {@code /mfa/enroll/verify}, {@code /mfa/recovery-codes/regenerate}).
 *
 * <p>Wraps the response in a {@link com.edushift.shared.api.ApiResponse}
 * envelope at the controller boundary; this is the inner payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MfaResponse(
		/**
		 * Plaintext base32 TOTP secret. Returned ONLY by
		 * {@code /mfa/enroll/start} so the FE can render a manual
		 * entry fallback (some users prefer typing the secret over
		 * scanning the QR).
		 */
		String secret,

		/**
		 * PNG data URL of the QR code (e.g. {@code data:image/png;base64,...})
		 * ready to be dropped into an {@code <img src="...">}.
		 * Returned ONLY by {@code /mfa/enroll/start}.
		 */
		String qrCodeDataUrl,

		/**
		 * Standard {@code otpauth://} URI. Returned ONLY by
		 * {@code /mfa/enroll/start} (useful for debugging / third-party
		 * tooling integration tests).
		 */
		String otpauthUri,

		/**
		 * Plaintext recovery codes (10 of them). Returned ONLY by
		 * {@code /mfa/enroll/verify} and {@code /mfa/recovery-codes/regenerate}.
		 * Shown to the user exactly once.
		 */
		List<String> recoveryCodes
) {

	public static MfaResponse enrollmentStart(String secret, String qr, String otpauth) {
		return new MfaResponse(secret, qr, otpauth, null);
	}

	public static MfaResponse recoveryCodes(List<String> codes) {
		return new MfaResponse(null, null, null, codes);
	}
}