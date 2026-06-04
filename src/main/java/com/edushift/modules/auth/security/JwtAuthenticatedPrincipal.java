package com.edushift.modules.auth.security;

import com.edushift.infrastructure.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Concrete implementation of {@link AuthenticatedPrincipal} backed by the
 * claims of a verified access JWT.
 *
 * <p>The {@code id} carried here is the user's <strong>publicUuid</strong>
 * (the JWT {@code sub} claim), <em>not</em> the internal {@code User.id}.
 * The filter (and therefore the principal) only has the JWT to work with,
 * so it cannot resolve the internal id without an extra database round-trip
 * — which we deliberately avoid on every authenticated request.
 *
 * <p>The downstream wiring is consistent with this choice:
 * <ul>
 *   <li>{@code AuthServiceImpl.currentUser()} reads {@code auth.getName()}
 *       and looks the user up by {@code publicUuid}.</li>
 *   <li>{@code SecurityAuditorAware} stores this same {@code publicUuid} in
 *       {@code created_by} / {@code updated_by} columns, which is fine as
 *       long as the choice stays consistent across the codebase.</li>
 * </ul>
 *
 * @param id         user.publicUuid (from JWT {@code sub})
 * @param tenantId   tenant.id (from JWT {@code tenant_id})
 * @param tenantSlug tenant.slug (from JWT {@code tenant_slug}); useful for logs / display
 * @param email      user.email (from JWT {@code email}); useful for UI / logs only
 */
public record JwtAuthenticatedPrincipal(
		UUID id,
		UUID tenantId,
		String tenantSlug,
		String email
) implements AuthenticatedPrincipal {

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public UUID getTenantId() {
		return tenantId;
	}

	@Override
	public String getUsername() {
		return email;
	}

	/**
	 * Friendly representation used by Spring Security in logs. Intentionally
	 * does not include the bearer token — that's stored as the credentials.
	 */
	@Override
	public String toString() {
		return "JwtAuthenticatedPrincipal[id=" + id
				+ ", tenantSlug=" + tenantSlug
				+ ", email=" + email + "]";
	}

}
