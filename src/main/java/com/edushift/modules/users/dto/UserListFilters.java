package com.edushift.modules.users.dto;

import com.edushift.modules.auth.entity.UserStatus;

/**
 * Query-time filters for {@code GET /v1/users}.
 *
 * <p>Each field is independently optional. The service composes them as
 * {@code AND} predicates against the {@code users} table. {@code null}
 * means "no filter on this dimension".
 *
 * <ul>
 *   <li>{@code search} — case-insensitive substring match against
 *       {@code email}, {@code first_name}, and {@code last_name}.
 *       Trimmed; blank → no filter.</li>
 *   <li>{@code status} — exact-equality match on the lifecycle column.</li>
 *   <li>{@code role} — array-contains match against the {@code roles}
 *       jsonb-ish column ({@code varchar[]}). Compared by name only.</li>
 * </ul>
 *
 * <p>This is a transport record, not a value object — it lives in
 * {@code dto/} so the controller can build it from query params and the
 * service can consume it without forcing every caller to know about
 * Specifications.
 */
public record UserListFilters(
		String search,
		UserStatus status,
		String role
) {

	public static UserListFilters empty() {
		return new UserListFilters(null, null, null);
	}

	public boolean hasAnyFilter() {
		return (search != null && !search.isBlank())
				|| status != null
				|| (role != null && !role.isBlank());
	}
}
