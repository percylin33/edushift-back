package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/forgot-password}.
 *
 * <p>The endpoint always responds 200 OK to avoid leaking which emails exist
 * (anti-enumeration). The tenant slug is required because EduShift is
 * multi-tenant — a single email can map to multiple users across different
 * tenants.
 *
 * @param email      user's email (validated for shape, not existence)
 * @param tenantSlug tenant the email belongs to (validated for shape, not existence)
 */
public record ForgotPasswordRequest(
		@NotBlank(message = "email is required")
		@Size(max = 255, message = "email must not exceed 255 characters")
		@Email(message = "email must be a valid address")
		String email,

		@NotBlank(message = "tenantSlug is required")
		@Size(max = 64, message = "tenantSlug must not exceed 64 characters")
		String tenantSlug
) {
}