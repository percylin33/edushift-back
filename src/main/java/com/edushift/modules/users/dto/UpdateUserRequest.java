package com.edushift.modules.users.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial-update payload for {@code PATCH /v1/users/{publicUuid}}.
 *
 * <p><strong>null = no change.</strong> Every field is optional; the service
 * only touches what the patch carries. Email and roles intentionally do
 * <em>not</em> live here:
 * <ul>
 *   <li>Email changes go through a dedicated re-verification flow (Sprint 9).</li>
 *   <li>Role changes go through {@code POST /{publicUuid}/roles} so we can
 *       gate them with {@code USERS:MANAGE_ROLES} when permission-based
 *       auth lands.</li>
 * </ul>
 */
public record UpdateUserRequest(
		@Size(min = 1, max = 100, message = "firstName must be between 1 and 100 characters")
		String firstName,

		@Size(min = 1, max = 100, message = "lastName must be between 1 and 100 characters")
		String lastName,

		@Pattern(
				regexp = "^[+0-9\\s\\-()]{6,32}$",
				message = "phone must contain only digits, spaces, dashes, parentheses, and an optional leading +"
		)
		@Size(max = 32, message = "phone must be at most 32 characters")
		String phone,

		@Size(max = 512, message = "avatarUrl must be at most 512 characters")
		String avatarUrl
) {
	/**
	 * Convenience guard so callers can short-circuit when there's nothing
	 * to merge. Returns true when every field is null.
	 */
	public boolean isEmpty() {
		return firstName == null && lastName == null && phone == null && avatarUrl == null;
	}
}
