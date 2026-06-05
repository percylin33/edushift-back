package com.edushift.modules.users.service;

import com.edushift.modules.users.dto.AssignRolesRequest;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListFilters;
import com.edushift.modules.users.dto.UserListItem;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Tenant-scoped administration of the {@code users} aggregate.
 *
 * <p>The aggregate root ({@link com.edushift.modules.auth.entity.User}) is
 * shared with the {@code auth} module — that's by design. {@code auth}
 * owns authentication concerns (login, JWT, refresh tokens), this module
 * owns lifecycle and management concerns (listing, role assignment,
 * disable/enable, profile updates by an admin).
 *
 * <h3>Authorization</h3>
 * Every method assumes the caller has already passed the
 * {@code @PreAuthorize("hasRole('TENANT_ADMIN')")} gate at the controller
 * boundary. The service does <em>not</em> re-check role membership; it does
 * however enforce <em>tenant safety</em> guardrails (see "Guardrails" below)
 * because those depend on data the {@code @PreAuthorize} expression cannot
 * see (e.g. "is this the last admin in the tenant?").
 *
 * <h3>Guardrails</h3>
 * <ul>
 *   <li><strong>Self-lockout:</strong> an admin cannot
 *       {@link #disableUser(UUID)} their own account.</li>
 *   <li><strong>Last-admin protection:</strong> an admin cannot
 *       {@link #assignRoles(UUID, AssignRolesRequest)} away the last
 *       {@code TENANT_ADMIN} of a tenant. Same protection applies to
 *       {@link #disableUser(UUID)} on the last admin.</li>
 *   <li><strong>Tenant isolation:</strong> the underlying repository is
 *       always queried in the current {@code TenantContext}, so a request
 *       for "user X" only resolves if X belongs to the caller's tenant.
 *       Cross-tenant access naturally surfaces as
 *       {@link com.edushift.shared.exception.ResourceNotFoundException}.</li>
 * </ul>
 */
public interface UserManagementService {

	/**
	 * Paginated list of users in the current tenant with optional filters.
	 * The result is always sorted in a way the controller decides (typically
	 * {@code lastLoginAt DESC}); the service itself does not impose ordering.
	 */
	Page<UserListItem> listUsers(UserListFilters filters, Pageable pageable);

	/**
	 * Resolves a single user by their public UUID. Throws
	 * {@link com.edushift.shared.exception.ResourceNotFoundException} when
	 * the UUID does not match any (non-deleted) user in the current tenant.
	 */
	UserDetailResponse getUser(UUID publicUuid);

	/**
	 * Applies a partial profile update in place. Returns the post-merge
	 * snapshot. Email and role changes are <em>not</em> in this method's
	 * surface — see {@link UpdateUserRequest} for the rationale.
	 */
	UserDetailResponse updateUser(UUID publicUuid, UpdateUserRequest request);

	/**
	 * Replaces the user's roles with the set in {@code request}. Throws
	 * {@code 400 INVALID_ROLE} when the request contains an unknown role
	 * name; throws {@code 409 LAST_ADMIN_PROTECTION} when the operation
	 * would strand the tenant without an admin.
	 */
	UserDetailResponse assignRoles(UUID publicUuid, AssignRolesRequest request);

	/**
	 * Marks the user as {@code SUSPENDED}. Idempotent (re-disabling a
	 * suspended account is a no-op); guards against self-lockout and
	 * last-admin removal.
	 */
	UserDetailResponse disableUser(UUID publicUuid);

	/**
	 * Returns a {@code SUSPENDED} user to {@code ACTIVE}. Idempotent on
	 * users already in {@code ACTIVE}. Users in
	 * {@code PENDING_VERIFICATION} cannot be enabled this way (they need
	 * to verify email first); the service surfaces a stable code so the
	 * UI can route to the right remediation.
	 */
	UserDetailResponse enableUser(UUID publicUuid);

	/**
	 * Initiates an admin-driven password reset. In the current sprint
	 * this method only flags the user's account; the actual reset email
	 * is delivered by the {@code notifications} module (Sprint 9).
	 */
	void resetPassword(UUID publicUuid);

}
