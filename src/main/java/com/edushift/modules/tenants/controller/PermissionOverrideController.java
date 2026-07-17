package com.edushift.modules.tenants.controller;

import com.edushift.modules.tenants.dto.RolePermissionOverrideDto;
import com.edushift.modules.tenants.dto.UpsertPermissionOverrideRequest;
import com.edushift.modules.tenants.service.PermissionOverrideService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D1 / F0.5 — Custom role permissions (QA plan 2026-07-02,
 * see {@code docs/qa/12-custom-permissions-feature.md}).
 *
 * <h3>Endpoints (under {@code /api/v1/tenants/me/permission-overrides})</h3>
 * <table>
 *   <caption>Permission override endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET </td><td>/tenants/me/permission-overrides</td>
 *       <td>TENANT_ADMIN</td>
 *       <td>{@link ApiResponse}&lt;List&lt;{@link RolePermissionOverrideDto}&gt;&gt;</td></tr>
 *   <tr><td>PUT </td><td>/tenants/me/permission-overrides</td>
 *       <td>TENANT_ADMIN</td>
 *       <td>{@link ApiResponse}&lt;{@link RolePermissionOverrideDto}&gt;</td></tr>
 * </table>
 *
 * <h3>Tenant isolation</h3>
 * The {@code tenantId} is taken from the authenticated session via
 * {@link CurrentUserProvider} and never accepted from the request body
 * — accepting it from the body would let a TA escalate the scope of
 * the write to a tenant they don't belong to. If the principal has
 * no tenant (SUPER_ADMIN), the endpoint throws {@link ForbiddenException}
 * because SUPER_ADMIN operates at the platform tier, not per tenant.
 *
 * <h3>Why we don't expose a DELETE / "reset to defaults" yet</h3>
 * Soft-delete is supported at the DB layer
 * ({@code role_permission_overrides.deleted}) but not exposed yet.
 * Sprint X.X / BE-X.X will add a POST {@code /reset-all} endpoint
 * under the same path. Until then, TAs can override any default back
 * to its platform value by sending {@code granted = <platform default>}.
 */
@Tag(name = "Permission Overrides", description = "D1 — TENANT_ADMIN customises LMS_* authorities for their tenant")
@RestController
@RequestMapping("/tenants/me/permission-overrides")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class PermissionOverrideController {

	private final PermissionOverrideService overrideService;
	private final CurrentUserProvider currentUserProvider;

	@Operation(summary = "List active role permission overrides for the current tenant")
	@GetMapping
	public ResponseEntity<ApiResponse<List<RolePermissionOverrideDto>>> list() {
		UUID tenantId = requireTenantId();
		return ResponseEntity.ok(ApiResponse.ok(overrideService.listAll(tenantId)));
	}

	@Operation(summary = "Upsert one (role, authority, granted) triple owned by the current tenant")
	@PutMapping
	public ResponseEntity<ApiResponse<RolePermissionOverrideDto>> upsert(
			@Valid @RequestBody UpsertPermissionOverrideRequest body
	) {
		UUID tenantId = requireTenantId();
		UUID actor = currentUserProvider.currentUserId().orElse(null);
		RolePermissionOverrideDto dto = overrideService.upsert(
				tenantId, body.role(), body.authority(), body.granted(), actor);
		return ResponseEntity.ok(ApiResponse.ok(dto));
	}

	private UUID requireTenantId() {
		return currentUserProvider.currentTenantId()
				.orElseThrow(() -> new ForbiddenException(
						"Permission overrides require a tenant context"));
	}
}
