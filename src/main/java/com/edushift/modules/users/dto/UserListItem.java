package com.edushift.modules.users.dto;

import com.edushift.modules.auth.entity.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight projection of a {@code User} for the list view.
 *
 * <p>Deliberately leaner than {@link UserDetailResponse}: list pages render
 * dozens of rows per request, so we ship only the columns the table cells
 * actually display. Heavier fields (e.g. {@code mfaEnabled}, {@code phone},
 * {@code avatarUrl}) are deferred to the detail endpoint.
 *
 * <p>Identity is exposed as {@code publicUuid}, never the internal numeric /
 * UUID primary key — that one is an implementation detail of the persistence
 * layer and must not leak across the API boundary.
 */
public record UserListItem(
		UUID publicUuid,
		String email,
		String firstName,
		String lastName,
		String fullName,
		UserStatus status,
		Set<String> roles,
		Instant lastLoginAt,
		Instant createdAt
) {
}
