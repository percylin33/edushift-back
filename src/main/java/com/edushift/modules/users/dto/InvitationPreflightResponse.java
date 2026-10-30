package com.edushift.modules.users.dto;

/** Public-safe projection consumed by the accept-invitation page. */
public record InvitationPreflightResponse(
		String email,
		String firstName,
		String lastName,
		String secondLastName,
		String tenantName,
		String tenantSlug
) {
	public InvitationPreflightResponse(String email, String firstName, String lastName,
			String tenantName, String tenantSlug) {
		this(email, firstName, lastName, null, tenantName, tenantSlug);
	}
}
