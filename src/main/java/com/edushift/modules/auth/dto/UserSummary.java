package com.edushift.modules.auth.dto;

import com.edushift.modules.auth.entity.UserStatus;
import java.util.UUID;

/**
 * Minimal user projection for listings, pickers and references where the
 * full {@link UserResponse} would be wasteful (e.g. assignment dropdowns,
 * activity timeline rows, audit views).
 */
public record UserSummary(
		UUID publicUuid,
		String fullName,
		String email,
		String avatarUrl,
		UserStatus status
) {
}
