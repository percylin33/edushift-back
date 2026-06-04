package com.edushift.modules.tenants.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-signup payload submitted to {@code POST /v1/tenants/register}.
 *
 * <h3>What the request creates atomically</h3>
 * One {@code tenants} row (status {@code PENDING}, plan {@code TRIAL},
 * 14-day trial window) plus one {@code users} row (status
 * {@code ACTIVE}, role {@code TENANT_ADMIN} once BE-2.4 lands) and
 * the matching {@code refresh_tokens} entry. Everything succeeds or
 * everything rolls back.
 *
 * <h3>Where credentials go after success</h3>
 * The endpoint returns an {@code AuthResponse} (bearer-shaped),
 * exactly like {@code /auth/login}, so the SPA can jump straight from
 * the registration form into the onboarding wizard without a second
 * round-trip to authenticate. The newly minted refresh token is the
 * client's resume credential — same lifecycle as any other session.
 *
 * <h3>Why the password rules are mild</h3>
 * Sprint 2 reuses the login's {@code @Size(min=1, max=128)} rule. A
 * dedicated password policy (entropy, breached-password check, MFA
 * enrollment) is out of scope here and lives in a future
 * "auth hardening" sprint. We do enforce {@code @NotBlank} so the
 * request can't ship an empty string past validation.
 *
 * <h3>Slug grammar</h3>
 * Mirrored from the {@code chk_tenants_slug_format} CHECK constraint
 * in V4 — kebab-case lowercase alphanumerics, 2-80 chars, no leading
 * or trailing dash. Catching it client-side keeps server errors out
 * of the happy path.
 */
public record RegisterTenantRequest(

		@NotBlank(message = "tenantName is required")
		@Size(min = 2, max = 200, message = "tenantName must be between 2 and 200 characters")
		String tenantName,

		@NotBlank(message = "tenantSlug is required")
		@Size(min = 2, max = 80, message = "tenantSlug length out of range")
		@Pattern(
				regexp = "^[a-z0-9]([a-z0-9-]{0,78}[a-z0-9])?$",
				message = "tenantSlug must be lowercase alphanumerics with optional dashes (kebab-case)"
		)
		String tenantSlug,

		@NotBlank(message = "adminEmail is required")
		@Email(message = "adminEmail must be a valid address")
		@Size(max = 254, message = "adminEmail must not exceed 254 characters")
		String adminEmail,

		@NotBlank(message = "adminPassword is required")
		@Size(min = 8, max = 128, message = "adminPassword must be between 8 and 128 characters")
		String adminPassword,

		@NotBlank(message = "adminFirstName is required")
		@Size(min = 1, max = 100, message = "adminFirstName length out of range")
		String adminFirstName,

		@NotBlank(message = "adminLastName is required")
		@Size(min = 1, max = 100, message = "adminLastName length out of range")
		String adminLastName

) {

	/** Defensive {@code toString} — never log the password verbatim. */
	@Override
	public String toString() {
		return "RegisterTenantRequest[tenantSlug=" + tenantSlug
				+ ", adminEmail=" + adminEmail
				+ ", adminFirstName=***, adminLastName=***, adminPassword=***]";
	}

}
