package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login credentials submitted by a client.
 * <p>
 * The {@code tenantSlug} is intentionally NOT in the body — it travels in the
 * {@code X-Tenant-Slug} header (or, in Sprint 2, in the subdomain) so that
 * a single endpoint can serve all tenants without exposing which slugs exist
 * via guess-by-body responses.
 *
 * @param email    user email; normalized to lowercase server-side
 * @param password raw password (never logged, never persisted)
 */
public record LoginRequest(

		@NotBlank(message = "Email is required")
		@Email(message = "Email must be a valid address")
		@Size(max = 254, message = "Email must not exceed 254 characters")
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 1, max = 128, message = "Password length out of range")
		String password
) {

	/** Defensive {@code toString} that masks the password. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
