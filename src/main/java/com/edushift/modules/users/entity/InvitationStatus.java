package com.edushift.modules.users.entity;

/**
 * Computed lifecycle of a {@link UserInvitation}.
 *
 * <p>Not persisted as a column — derived from the
 * {@code (acceptedAt, cancelledAt, expiresAt)} trio at read time. The
 * mapping rules live in {@code UserInvitation.isAccepted/isCancelled/isExpired}
 * and the value class is exposed only on response DTOs.
 */
public enum InvitationStatus {
	PENDING,
	ACCEPTED,
	CANCELLED,
	EXPIRED
}
