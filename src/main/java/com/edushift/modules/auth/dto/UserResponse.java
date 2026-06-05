package com.edushift.modules.auth.dto;

import com.edushift.modules.auth.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Public representation of a {@link com.edushift.modules.auth.entity.User}.
 * <p>
 * Exposes <em>only</em> the external {@code publicUuid} (never the internal
 * UUIDv7 PK) and never the password hash. {@code JsonInclude.NON_NULL} keeps
 * the payload compact when optional fields (phone, avatar, lastLogin) are
 * unset.
 *
 * <h3>Why include {@code roles}</h3>
 * The frontend uses role-gated guards (e.g. {@code TENANT_ADMIN} on
 * {@code /users}) and renders role-aware navigation immediately after login
 * without an extra round-trip. {@code /auth/me} is the canonical
 * "who am I, fully" endpoint, so role names belong here. The set is
 * always non-null and uses UPPER_CASE names matching the
 * {@link com.edushift.modules.auth.entity.UserRole} enum so the frontend
 * can narrow them with {@code toRoles(...)}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
		UUID publicUuid,
		String firstName,
		String lastName,
		String fullName,
		String email,
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
