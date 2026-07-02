package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/mfa/challenge}.
 *
 * <p>Carried in the body (NOT in the {@code mfaToken}) so the code is
 * not stored in any HTTP intermediary log. The {@code mfaToken} is sent
 * as the {@code Authorization: Bearer ...} header.
 *
 * @param code either a 6-digit TOTP code OR a 10-character recovery code
 *             (optionally with a dash at position 5)
 */
public record MfaChallengeRequest(
		@NotBlank(message = "code is required")
		@Size(max = 64, message = "code must not exceed 64 characters")
		String code
) {
}