package com.edushift.modules.admin.superadmin.mapper;

import com.edushift.modules.admin.superadmin.dto.SuperAdminSummary;
import com.edushift.modules.auth.entity.User;
import java.util.List;

/**
 * Sprint 15 / F-06 / H-04: SUPER_ADMIN entity → wire DTO. Trims roles to
 * type-safe strings so the FE never has to bind to the raw varchar[].
 */
public final class SuperAdminMapper {

	private SuperAdminMapper() {}

	public static SuperAdminSummary toSummary(User user) {
		if (user == null) return null;
		List<String> roles = user.getRoleNames() == null
				? List.of()
				: List.copyOf(user.getRoleNames());
		return new SuperAdminSummary(
				user.getPublicUuid(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.getStatus() != null ? user.getStatus().name() : null,
				user.isMfaEnabled(),
				user.getLastLoginAt(),
				user.getCreatedAt(),
				roles);
	}
}
