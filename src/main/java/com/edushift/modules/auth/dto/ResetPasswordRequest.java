package com.edushift.modules.auth.dto;

import com.edushift.shared.validation.annotations.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/reset-password}.
 *
 * @param token         reset token previously issued by {@code /auth/forgot-password}
 * @param newPassword   the new password (must satisfy the project's
 *                      {@link ValidPassword} policy)
 * @param confirmPassword optional client-side confirmation; if present,
 *                        the controller checks it matches {@code newPassword}
 *                        before any service call (avoids round-tripping an
 *                        obvious typo through the service layer)
 */
public record ResetPasswordRequest(
		@NotBlank(message = "token is required")
		@Size(max = 4096, message = "token must not exceed 4096 characters")
		String token,

		@NotBlank(message = "newPassword is required")
		@ValidPassword
		String newPassword,

		@Size(max = 255, message = "confirmPassword must not exceed 255 characters")
		String confirmPassword
) {
}