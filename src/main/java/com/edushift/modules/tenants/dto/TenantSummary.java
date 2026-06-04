package com.edushift.modules.tenants.dto;

import com.edushift.modules.tenants.entity.TenantStatus;
import java.util.UUID;

/**
 * Public-safe projection of a {@link com.edushift.modules.tenants.entity.Tenant}.
 *
 * <h3>What lives here</h3>
 * Only fields safe to expose to anyone hitting
 * {@code GET /v1/tenants/by-slug/{slug}} <strong>without authentication</strong>:
 * the externally-known identifier ({@code slug}), human-readable name,
 * lifecycle status (so the login page can refuse to render a credential
 * form for a {@code SUSPENDED} tenant), and the visual branding the
 * front needs to theme the login screen.
 *
 * <h3>What does NOT live here</h3>
 * Everything that could leak business intel: {@code plan},
 * {@code trialEndsAt}, {@code featureFlags}, {@code settings},
 * {@code maxStudents}, {@code maxTeachers}, {@code customDomain},
 * audit timestamps. Those flow through {@link TenantResponse} which is
 * gated by authentication.
 *
 * Defense in depth: the DTO shape is the contract, not just convention.
 * If {@link TenantSummary} grows a sensitive field by accident, every
 * caller of the public endpoint immediately sees it — easier to spot
 * in code review than a silently-leaking field in {@link TenantResponse}.
 */
public record TenantSummary(
		UUID publicUuid,
		String name,
		String slug,
		TenantStatus status,
		BrandingDto branding
) {
}
