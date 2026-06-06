package com.edushift.modules.users.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published by {@code UserInvitationServiceImpl.acceptInvitation} after
 * the new {@code User} is persisted and the invitation marked
 * {@code accepted}, but BEFORE the outer transaction commits — so that
 * synchronous {@code @EventListener} consumers (e.g.
 * {@code teachers.TeacherInvitationListener}) can perform their atomic
 * domain reactions inside the same Hibernate session.
 *
 * <p>Fired from inside {@code TenantContext.runAs(...)} so listeners
 * see the correct tenant scope on every JPA query.</p>
 *
 * @param invitationId       internal id of the invitation row
 * @param invitationPublicUuid stable external UUID of the invitation
 * @param userId             internal id of the freshly-created user
 * @param userPublicUuid     stable external UUID of the user
 * @param tenantId           tenant scope (matches the runAs context)
 * @param metadata           snapshot of the invitation's metadata —
 *                           callers MUST NOT mutate this map; it is
 *                           wrapped {@code Map.copyOf(...)} on publish
 * @param acceptedAt         when the invitation was marked accepted
 */
public record InvitationAcceptedEvent(
		UUID invitationId,
		UUID invitationPublicUuid,
		UUID userId,
		UUID userPublicUuid,
		UUID tenantId,
		Map<String, Object> metadata,
		Instant acceptedAt
) {
}
