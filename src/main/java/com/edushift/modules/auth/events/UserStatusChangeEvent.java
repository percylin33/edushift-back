package com.edushift.modules.auth.events;

import com.edushift.modules.auth.entity.UserStatus;
import java.util.UUID;

/**
 * Published by the users module whenever a {@code User} transitions
 * between {@link UserStatus} states. The auth module listens to this and
 * revokes all active refresh tokens (closes <strong>DEBT-AUTH-4</strong>).
 *
 * <h3>Why a discrete event (not a Spring {@code ApplicationEvent})</h3>
 * Keeping this in the auth module's package makes the cross-module
 * dependency explicit and easy to grep. It is published via Spring's
 * {@code ApplicationEventPublisher} by the users module and consumed by
 * {@code com.edushift.modules.auth.listener.AuthEventListener}.
 *
 * <h3>What NOT to put in the event</h3>
 * No PII (email, password hash). Only references — lookups happen in the
 * listener, which has the right transactional and tenant context.
 *
 * @param userPublicUuid    the UUID the user is known by externally.
 * @param oldStatus         the previous status (nullable for create).
 * @param newStatus         the new status (what triggered the event).
 * @param reason            human-readable reason (e.g. "admin-disable",
 *                          "self-signup"). Not localized.
 * @param actorPublicUuid   who triggered the change. {@code null} when
 *                          system-triggered (e.g. lockout auto-unlock).
 */
public record UserStatusChangeEvent(
		UUID userPublicUuid,
		UserStatus oldStatus,
		UserStatus newStatus,
		String reason,
		UUID actorPublicUuid
) {
}
