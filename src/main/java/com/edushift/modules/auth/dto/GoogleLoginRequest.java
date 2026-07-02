package com.edushift.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/v1/auth/google}.
 *
 * <p>The {@code tenantSlug} is also required but lives in the
 * {@code X-Tenant-Slug} HTTP header (mirroring {@code POST /auth/login}).
 * We deliberately don't accept it in the body because tenant identity is
 * metadata about the request, not a user-controlled field — keeping it in
 * the header (where {@link com.edushift.modules.tenants.controller.TenantController}
 * already validates it) prevents a buggy client from logging into the
 * wrong tenant by mistake.
 *
 * @param idToken raw JWT string returned by Google's OAuth popup
 *                (or by the {@code angularx-social-login} library on the FE)
 */
public record GoogleLoginRequest(
		@NotBlank(message = "idToken is required")
		@Size(min = 32, max = 4096, message = "idToken length is out of bounds")
		String idToken
) {
	@Override
	public String toString() {
		return "GoogleLoginRequest[idToken=***]";
	}
}