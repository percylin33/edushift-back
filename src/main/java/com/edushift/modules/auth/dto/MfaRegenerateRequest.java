package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/mfa/recovery-codes/regenerate}.
 *
 * @param currentPassword the user's current password (proof of identity
 *                        before re-issuing sensitive credentials)
 */
public record MfaRegenerateRequest(
		@NotBlank(message = "currentPassword is required")
		@Size(max = 255, message = "currentPassword must not exceed 255 characters")
		String currentPassword
) {
}