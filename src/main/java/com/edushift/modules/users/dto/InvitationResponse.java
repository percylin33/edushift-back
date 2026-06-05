package com.edushift.modules.users.dto;

import com.edushift.modules.users.entity.InvitationStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-facing projection of a {@code UserInvitation}.
 *
 * <p>Includes the {@code token} on the create response so the admin can
 * copy the resulting invitation link to the clipboard right away —
 * Sprint 9 will replace this with an automatic email delivery, but
 * during early sprints "show the link, let the admin send it" is
 * a simpler product surface than half-mocked email.
 *
 * <p>List endpoints (multiple invitations) intentionally omit the
 * token: there's no need for an admin to be able to grab any
 * existing invitation's token after creation, and treating tokens
 * as <em>write-once-readable</em> reduces the blast radius of an
 * audit-log leak.
 */
public record InvitationResponse(
		UUID publicUuid,
		String email,
		String firstName,
		String lastName,
		Set<String> roles,
		InvitationStatus status,
		String token,
		Instant expiresAt,
		Instant acceptedAt,
		Instant cancelledAt,
		Instant createdAt
) {

	/**
	 * List-view variant: {@code token} is dropped on purpose. Used by
	 * the mapper when projecting collections.
	 */
	public InvitationResponse withoutToken() {
		return new InvitationResponse(
				publicUuid, email, firstName, lastName, roles,
				status, null, expiresAt, acceptedAt, cancelledAt, createdAt);
	}
}
