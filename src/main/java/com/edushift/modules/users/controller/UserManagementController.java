package com.edushift.modules.users.controller;

import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.users.dto.AssignRolesRequest;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListFilters;
import com.edushift.modules.users.dto.UserListItem;
import com.edushift.modules.users.service.UserManagementService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code users} management module.
 *
 * <h3>Endpoints (under {@code /api/v1/users})</h3>
 * <table>
 *   <caption>User management endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                          </td><td>TENANT_ADMIN</td><td>{@link Page}&lt;{@link UserListItem}&gt;</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}              </td><td>TENANT_ADMIN</td><td>{@link UserDetailResponse}</td></tr>
 *   <tr><td>PATCH </td><td>/{publicUuid}              </td><td>TENANT_ADMIN</td><td>{@link UserDetailResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/roles        </td><td>TENANT_ADMIN</td><td>{@link UserDetailResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/disable      </td><td>TENANT_ADMIN</td><td>{@link UserDetailResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/enable       </td><td>TENANT_ADMIN</td><td>{@link UserDetailResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/reset-password</td><td>TENANT_ADMIN</td><td>202 Accepted</td></tr>
 * </table>
 *
 * <p>All write paths and the list+detail reads are gated by
 * {@code @PreAuthorize("hasRole('TENANT_ADMIN')")}. Sprint 3 keeps the
 * gate role-based; the permission-based variant
 * ({@code USERS:READ} / {@code USERS:UPDATE} / {@code USERS:MANAGE_ROLES})
 * lands when the permission catalog is introduced (post-Sprint 3).
 */
@RestController
@RequestMapping("/users")
@Validated
@RequiredArgsConstructor
@Tag(name = "Users", description = "Tenant-scoped user management: list, profile, roles, lifecycle")
public class UserManagementController {

	private final UserManagementService service;

	// ===========================================================================
	// Read
	// ===========================================================================

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List users in the current tenant (TENANT_ADMIN)",
			description = "Paginated list with optional filters: case-insensitive "
					+ "substring search across email/firstName/lastName, exact "
					+ "status, and role-membership. Default sort is by "
					+ "createdAt DESC; override via the standard `sort` query param."
	)
	public ResponseEntity<Page<UserListItem>> list(
			@Parameter(description = "Substring match on email/firstName/lastName")
			@RequestParam(required = false) String search,
			@Parameter(description = "Filter by exact lifecycle status")
			@RequestParam(required = false) UserStatus status,
			@Parameter(description = "Filter by role name (e.g. TENANT_ADMIN, TEACHER)")
			@RequestParam(required = false) String role,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		UserListFilters filters = new UserListFilters(search, status, role);
		Page<UserListItem> page = service.listUsers(filters, pageable);
		return ResponseEntity.ok(page);
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Get a user by public UUID (TENANT_ADMIN)",
			description = "Full projection: profile, security flags, roles, and "
					+ "lifecycle timestamps. 404 RESOURCE_NOT_FOUND when the "
					+ "UUID does not match a user in the current tenant."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> getOne(@PathVariable UUID publicUuid) {
		UserDetailResponse response = service.getUser(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	// ===========================================================================
	// Write
	// ===========================================================================

	@PatchMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Partial update of a user's profile (TENANT_ADMIN)",
			description = "Field-by-field merge. Null = no change; blank string "
					+ "on a nullable field clears it. Email and roles have "
					+ "dedicated endpoints — they are not in this surface."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateUserRequest request
	) {
		UserDetailResponse response = service.updateUser(publicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{publicUuid}/roles")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Replace a user's roles (TENANT_ADMIN)",
			description = "Wholesale replacement: persists exactly the set in "
					+ "the body. 422 INVALID_ROLE on unknown role names; "
					+ "409 LAST_ADMIN_PROTECTION when the operation would "
					+ "strand the tenant without an active TENANT_ADMIN."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> assignRoles(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody AssignRolesRequest request
	) {
		UserDetailResponse response = service.assignRoles(publicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{publicUuid}/disable")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Disable (suspend) a user (TENANT_ADMIN)",
			description = "Idempotent on already-SUSPENDED users. 422 SELF_LOCKOUT "
					+ "if the admin targets their own account; 409 "
					+ "LAST_ADMIN_PROTECTION if disabling the user would "
					+ "leave the tenant without an active admin."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> disable(@PathVariable UUID publicUuid) {
		UserDetailResponse response = service.disableUser(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{publicUuid}/enable")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Re-enable a suspended user (TENANT_ADMIN)",
			description = "Promotes SUSPENDED → ACTIVE. Idempotent on already-"
					+ "ACTIVE users. Refuses other source statuses with 409 "
					+ "USER_NOT_ENABLEABLE so admins use the right remediation."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> enable(@PathVariable UUID publicUuid) {
		UserDetailResponse response = service.enableUser(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{publicUuid}/reset-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Trigger an admin-driven password reset (TENANT_ADMIN)",
			description = "Logs the intent for the target user. Sprint 9 wires "
					+ "this to the notifications module so an actual reset email "
					+ "is delivered. Returns 202 Accepted to convey 'request "
					+ "queued; out-of-band delivery to follow'."
	)
	public ResponseEntity<Void> resetPassword(@PathVariable UUID publicUuid) {
		service.resetPassword(publicUuid);
		return ResponseEntity.accepted().build();
	}

}
