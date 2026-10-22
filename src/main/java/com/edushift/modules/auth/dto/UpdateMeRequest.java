package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile patch for {@code PATCH /auth/me} (CRED-7 first login).
 * Identity-sensitive fields other than email stay on dedicated endpoints.
 */
public record UpdateMeRequest(
		@Size(max = 100) String firstName,
		@Size(max = 100) String lastName,
		@Size(max = 32) String phone,
		@Email @Size(max = 254) String email,
		@Size(max = 100) String secondLastName
) {
	public UpdateMeRequest(String firstName, String lastName, String phone, String email) {
		this(firstName, lastName, phone, email, null);
	}
}
