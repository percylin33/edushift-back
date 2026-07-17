package com.edushift.modules.tenants.dto;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.entity.RolePermissionOverride;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound shape of {@code GET /v1/tenants/me/permission-overrides}
 * (D1 / F0.5, QA plan 2026-07-02).
 *
 * <p>Returns the platform defaults merged with active overrides so the
 * UI can render the full matrix with one round trip. Use
 * {@link #granted} and {@link #isOverride} together to drive the
 * checkbox tri-state UI:</p>
 *
 * <ul>
 *   <li>{@code isOverride=false, granted=true}  → platform default ON.</li>
 *   <li>{@code isOverride=false, granted=false} → platform default OFF.</li>
 *   <li>{@code isOverride=true,  granted=true}  → TA explicitly enabled.</li>
 *   <li>{@code isOverride=true,  granted=false} → TA explicitly disabled.</li>
 * </ul>
 */
public record RolePermissionOverrideDto(
		UUID publicUuid,
		UserRole role,
		String authority,
		boolean granted,
		boolean isOverride,
		Instant updatedAt
) {

	public static RolePermissionOverrideDto platformDefault(UserRole role, String authority, boolean granted) {
		return new RolePermissionOverrideDto(
				null,
				role,
				authority,
				granted,
				false,
				null
		);
	}

	public static RolePermissionOverrideDto from(RolePermissionOverride o) {
		return new RolePermissionOverrideDto(
				o.getPublicUuid(),
				o.getRole(),
				o.getAuthority(),
				o.isGranted(),
				true,
				o.getUpdatedAt()
		);
	}
}
