package com.edushift.modules.auth.dto;

import com.edushift.shared.validation.annotations.ValidPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload for a user-initiated password change.
 * <p>
 * Both values are strings purely for transport: the service layer re-hashes
 * {@code newPassword} and verifies {@code currentPassword} against the
 * stored digest before persisting anything. Neither value is logged or
 * echoed back.
 */
public record ChangePasswordRequest(

		@NotBlank
		String currentPassword,

		@ValidPassword
		String newPassword
) {
}
