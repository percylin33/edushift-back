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
 * <h3>Why include {@code roles} and {@code permissions}</h3>
 * The frontend uses role-gated guards (e.g. {@code TENANT_ADMIN} on
 * {@code /users}) and permission-gated guards (e.g. {@code LMS_PAYMENT_ADMIN}
 * on the admin payments surface) and renders role-aware navigation
 * immediately after login without an extra round-trip. {@code /auth/me} is
 * the canonical "who am I, fully" endpoint, so role names and the granular
 * {@code LMS_*} authority strings belong here.
 *
 * <p>Role names use UPPER_CASE matching the
 * {@link com.edushift.modules.auth.entity.UserRole} enum. Permission strings
 * mirror {@link com.edushift.shared.security.LmsAuthorities} verbatim so the
 * frontend's {@code permissionGuard} and {@code HasPermissionDirective} can
 * match them one-to-one (see {@code edushift-front Permission.Lms*}).
 *
 * <p>Closes part of DEBT-SEC-1 (LMS authority surface). The remaining
 * {@code domain:action} permissions (e.g. {@code students:read},
 * {@code payments:read}) live forward-looking in
 * {@code edushift-front/.../permission.enum.ts} and are still gated on the
 * client by {@code roles: [...]} until the security sprint introduces the
 * relacional role↔domain:action model.
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
		Set<String> permissions,
		Instant lastLoginAt,
		Instant createdAt,
		Instant updatedAt
) {
}
