package com.edushift.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Body of {@code POST /v1/users/invitations}.
 *
 * <p>The recipient's profile fields are required — admins must
 * commit to a name when they invite, both because the preflight UI
 * displays it ("Welcome, {firstName}!") and because nameless accounts
 * are an antipattern in audit logs. Roles are required because an
 * empty-roles user cannot do anything; an admin who wants that should
 * be using {@code disable} instead.
 */
public record CreateInvitationRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be a valid address")
		@Size(max = 254, message = "email must be at most 254 characters")
		String email,

		@NotBlank(message = "firstName is required")
		@Size(min = 1, max = 100, message = "firstName must be between 1 and 100 characters")
		String firstName,

		@NotBlank(message = "lastName is required")
		@Size(min = 1, max = 100, message = "lastName must be between 1 and 100 characters")
		String lastName,

		@NotNull(message = "roles must not be null")
		@NotEmpty(message = "roles must contain at least one role")
		Set<String> roles
) {
}
