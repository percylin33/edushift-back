package com.edushift.modules.users.dto;

/**
 * Public-safe projection consumed by the accept-invitation page before
 * the recipient submits a password.
 *
 * <p>Only fields that are safe to surface on the open internet:
 * the recipient's name (so the page can greet them) and the tenant's
 * name (so the page can confirm <em>which school</em> they are joining
 * — important context when admins manage multiple tenants from the
 * same address). Email is included because the form pre-fills it and
 * the recipient can sanity-check the destination.
 *
 * <p>Notably absent: tenant id, slug, role list, and any auditing
 * field. Those are admin-internal.
 */
public record InvitationPreflightResponse(
		String email,
		String firstName,
		String lastName,
		String tenantName
) {
}
