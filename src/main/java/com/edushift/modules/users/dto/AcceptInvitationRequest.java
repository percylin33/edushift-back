package com.edushift.modules.users.dto;

import com.edushift.shared.validation.annotations.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/users/invitations/accept}.
 *
 * <p>The {@code token} is the high-entropy secret the admin handed out;
 * the {@code password} is what the recipient picks for their new
 * account. Both are validated at the same security baseline as
 * {@code CreateUserRequest} (8-72 chars, mixed case + digit + special)
 * so the two onboarding paths share a consistent policy — and an
 * attacker cannot bypass the {@code @StrongPassword} composite rule by
 * picking a weak password during invitation acceptance.
 *
 * <p>Closes DEBT-USR-2: the previous {@code @Size(min=8)} only enforced
 * length, allowing trivial passwords like {@code "12345678"} on
 * invitation-accept.
 */
public record AcceptInvitationRequest(
		@NotBlank(message = "token is required")
		@Size(min = 16, max = 128, message = "token must be between 16 and 128 characters")
		String token,

		@ValidPassword
		String password
) {
}
