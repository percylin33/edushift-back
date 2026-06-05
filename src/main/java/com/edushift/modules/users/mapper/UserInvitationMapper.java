package com.edushift.modules.users.mapper;

import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.modules.users.entity.UserInvitation;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Maps {@link UserInvitation} entities to API DTOs.
 *
 * <p>The status is computed at projection time from the lifecycle
 * timestamps — see {@link UserInvitation}'s class-level Javadoc for
 * the rationale.
 */
@Component
public class UserInvitationMapper {

	/**
	 * Full projection (with token). Used by {@code POST /invitations}
	 * so the admin can grab the token immediately.
	 */
	public InvitationResponse toResponseWithToken(UserInvitation invitation, Instant now) {
		return new InvitationResponse(
				invitation.getPublicUuid(),
				invitation.getEmail(),
				invitation.getFirstName(),
				invitation.getLastName(),
				invitation.getRoleNames(),
				deriveStatus(invitation, now),
				invitation.getToken(),
				invitation.getExpiresAt(),
				invitation.getAcceptedAt(),
				invitation.getCancelledAt(),
				invitation.getCreatedAt()
		);
	}

	/**
	 * Token-stripped projection. Used by list endpoints to avoid
	 * re-exposing tokens after they were initially shown to the admin.
	 */
	public InvitationResponse toResponse(UserInvitation invitation, Instant now) {
		return toResponseWithToken(invitation, now).withoutToken();
	}

	/**
	 * Derives the lifecycle state from the timestamps on the invitation.
	 * Order matters: {@code ACCEPTED} and {@code CANCELLED} are terminal
	 * (they take precedence over {@code EXPIRED}, which is a passive
	 * transition); a freshly-created invitation that's already past
	 * its TTL surfaces as {@code EXPIRED} so the UI can offer "resend".
	 */
	public InvitationStatus deriveStatus(UserInvitation invitation, Instant now) {
		if (invitation.isAccepted()) return InvitationStatus.ACCEPTED;
		if (invitation.isCancelled()) return InvitationStatus.CANCELLED;
		if (invitation.isExpired(now)) return InvitationStatus.EXPIRED;
		return InvitationStatus.PENDING;
	}
}
