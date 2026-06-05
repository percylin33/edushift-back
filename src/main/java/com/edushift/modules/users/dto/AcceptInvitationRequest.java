package com.edushift.modules.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/users/invitations/accept}.
 *
 * <p>The {@code token} is the high-entropy secret the admin handed out;
 * the {@code password} is what the recipient picks for their new
 * account. Same minimum length as {@code RegisterTenantRequest} so the
 * two onboarding paths share a security baseline.
 */
public record AcceptInvitationRequest(
		@NotBlank(message = "token is required")
		@Size(min = 16, max = 128, message = "token must be between 16 and 128 characters")
		String token,

		@NotBlank(message = "password is required")
		@Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
		String password
) {
}
