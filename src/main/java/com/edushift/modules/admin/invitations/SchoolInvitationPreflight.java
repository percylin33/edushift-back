package com.edushift.modules.admin.invitations;

/**
 * Public preflight for {@code GET /v1/tenants/invitations/by-token/{token}}.
 * Only the invited email is exposed — no tenant exists yet.
 */
public record SchoolInvitationPreflight(String email) {
}
