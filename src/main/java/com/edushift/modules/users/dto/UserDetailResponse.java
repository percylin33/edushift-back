package com.edushift.modules.users.dto;

import com.edushift.modules.auth.entity.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Full user projection returned by {@code GET /v1/users/{publicUuid}}.
 *
 * <p>Includes every column an admin needs to manage another user's account:
 * profile fields, security flags ({@code emailVerified}, {@code mfaEnabled}),
 * lifecycle timestamps, and the resolved role names. Password hashes are
 * <em>never</em> included — those live in the persistence layer alone.
 *
 * <p>Same shape as {@link UserListItem} for the overlapping fields, so the
 * frontend can reuse adapters between the two views.
 */
public record UserDetailResponse(
		UUID publicUuid,
		String email,
		String firstName,
		String lastName,
		String fullName,
		String phone,
		String avatarUrl,
		UserStatus status,
		boolean emailVerified,
		boolean mfaEnabled,
		Set<String> roles,
		Instant lastLoginAt,
		Instant createdAt,
		Instant updatedAt
) {
}
