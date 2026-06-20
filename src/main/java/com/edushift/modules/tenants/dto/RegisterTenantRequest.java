package com.edushift.modules.tenants.dto;

import com.edushift.shared.validation.annotations.ValidPassword;
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
 * <h3>Password policy</h3>
 * The {@code adminPassword} field is bound to {@link ValidPassword} (8-72
 * chars, mixed case + digit + special) so the self-signup flow applies
 * the same baseline as {@code CreateUserRequest} and
 * {@code AcceptInvitationRequest}. Without this guard a tenant founder
 * could create their account with {@code "12345678"} and bypass the
 * policy enforced elsewhere. Closes DEBT-USR-2.
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

		@ValidPassword
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
