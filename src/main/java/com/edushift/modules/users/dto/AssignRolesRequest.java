package com.edushift.modules.users.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Wholesale-replacement payload for {@code POST /v1/users/{publicUuid}/roles}.
 *
 * <p><strong>Replace, not patch:</strong> the request carries the
 * <em>desired</em> set of roles. The service persists exactly what's in
 * {@link #roles()}, even if that means dropping previously assigned ones.
 * This keeps the contract explicit (no toggle ambiguity) and matches how
 * most admin UIs render role pickers.
 *
 * <p>The set must be non-null and non-empty; "no roles" is expressed by
 * {@link com.edushift.modules.users.service.UserManagementService#disableUser}
 * (account-level lockout) rather than by sending an empty array (semantic
 * footgun: an empty array would create an account that exists but cannot
 * do anything, which is rarely what an admin actually wants).
 *
 * <p>Each entry is the {@code name()} of a value in
 * {@link com.edushift.modules.auth.entity.UserRole}. Unknown names cause
 * a {@code 400 INVALID_ROLE} response from the service layer.
 */
public record AssignRolesRequest(
		@NotNull(message = "roles must not be null")
		@NotEmpty(message = "roles must contain at least one role")
		Set<String> roles
) {
}
