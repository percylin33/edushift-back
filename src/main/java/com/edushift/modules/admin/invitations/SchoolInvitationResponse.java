package com.edushift.modules.admin.invitations;

import com.edushift.modules.users.entity.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Super-Admin projection of a school invitation.
 *
 * <p>{@code token} and {@code setupUrl} are present on create/resend so the
 * operator can copy the link if SMTP is off. List endpoints omit the token.
 */
public record SchoolInvitationResponse(
		UUID publicUuid,
		String email,
		InvitationStatus status,
		String token,
		String setupUrl,
		boolean emailSent,
		Instant expiresAt,
		Instant acceptedAt,
		Instant cancelledAt,
		Instant createdAt
) {

	public SchoolInvitationResponse withoutToken() {
		return new SchoolInvitationResponse(
				publicUuid, email, status, null, null, emailSent,
				expiresAt, acceptedAt, cancelledAt, createdAt);
	}
}
