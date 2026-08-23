package com.edushift.modules.admin.invitations;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSchoolInvitationRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be a valid address")
		@Size(max = 254, message = "email must be at most 254 characters")
		String email
) {
}
