package com.edushift.modules.auth.repository;

import com.edushift.modules.auth.entity.UserGoogleIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link UserGoogleIdentity}.
 *
 * <p>All find operations rely on Hibernate's {@code @TenantId} discriminator:
 * when {@link com.edushift.shared.multitenancy.TenantContext} is set,
 * queries are auto-scoped by {@code tenant_id}. The
 * {@code ...AndTenantId} overloads remain for system / admin paths that run
 * with no tenant in context (e.g. background jobs, super-admin tools).
 */
@Repository
public interface UserGoogleIdentityRepository
		extends JpaRepository<UserGoogleIdentity, UUID> {

	/**
	 * Looks up the active refresh token row for a given user. The partial
	 * unique index {@code uk_user_google_tokens_user_active} guarantees at
	 * most one non-revoked row per user.
	 */
	Optional<UserGoogleIdentity> findByUserIdAndRevokedAtIsNull(UUID userId);

	/**
	 * System / admin overload — bypasses Hibernate's tenant filter by
	 * passing the tenant id explicitly. Use this from background jobs or
	 * when {@code TenantContext} is not yet bound.
	 */
	Optional<UserGoogleIdentity> findByUserIdAndTenantIdAndRevokedAtIsNull(
			UUID userId, UUID tenantId);
}