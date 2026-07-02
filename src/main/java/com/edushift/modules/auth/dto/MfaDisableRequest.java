package com.edushift.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/mfa/disable}.
 *
 * @param currentPassword the user's current password (proof of identity)
 * @param mfaCode         either a 6-digit TOTP code or a 10-char recovery code
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MfaDisableRequest(
		@NotBlank(message = "currentPassword is required")
		@Size(max = 255, message = "currentPassword must not exceed 255 characters")
		String currentPassword,

		@NotBlank(message = "mfaCode is required")
		@Size(max = 64, message = "mfaCode must not exceed 64 characters")
		String mfaCode
) {
}