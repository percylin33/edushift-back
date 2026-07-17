package com.edushift.modules.admin.superadmin.dto;

import com.edushift.shared.validation.annotations.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sprint 15 / F-06 / H-04: payload for
 * {@code POST /admin/super-admins}. The new SUPER_ADMIN is created in the
 * {@code edushift-system} sentinel tenant with an explicit
 * {@code mustResetPassword=true} flag forced via the same sentinel hash
 * mechanism used in {@code DevDataInitializer}.
 *
 * <p>The first login yields an onboarding token (H-02) — no manual
 * password hashing is exposed to the caller.</p>
 */
public record CreateSuperAdminRequest(
		@NotBlank @ValidEmail @Size(max = 254) String email,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName
) {}
