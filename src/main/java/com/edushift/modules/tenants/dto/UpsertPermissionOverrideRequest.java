package com.edushift.modules.tenants.dto;

import com.edushift.modules.auth.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Body of {@code PUT /v1/tenants/me/permission-overrides}
 * (D1 / F0.5, QA plan 2026-07-02).
 *
 * <p>Represents a single (role, authority, granted) triple owned by the
 * caller's tenant. The {@code tenantId} is taken from the session, never
 * from the body — this is the only way to keep multi-tenant isolation
 * trustworthy. Accepting tenant from the body would let a TA escalate
 * the scope of a write to a tenant they don't belong to.</p>
 */
public record UpsertPermissionOverrideRequest(

		@NotNull
		UserRole role,

		@NotBlank
		@Pattern(
				regexp = "^LMS_[A-Z_]+$",
				message = "authority must match LMS_* (whitelist enforced by CHECK constraint)"
		)
		String authority,

		boolean granted
) { }
