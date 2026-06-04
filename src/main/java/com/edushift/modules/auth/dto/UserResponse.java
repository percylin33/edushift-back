package com.edushift.modules.auth.dto;

import com.edushift.modules.auth.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a {@link com.edushift.modules.auth.entity.User}.
 * <p>
 * Exposes <em>only</em> the external {@code publicUuid} (never the internal
 * UUIDv7 PK) and never the password hash. {@code JsonInclude.NON_NULL} keeps
 * the payload compact when optional fields (phone, avatar, lastLogin) are
 * unset.
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
		Instant lastLoginAt,
		Instant createdAt,
		Instant updatedAt
) {
}
