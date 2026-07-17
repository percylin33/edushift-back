package com.edushift.infrastructure.multitenancy;

import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Hibernate hook that feeds the current tenant id from {@link TenantContext}.
 * <p>
 * Wired through {@code MultiTenancyConfiguration}. Once registered, Hibernate
 * uses it to:
 * <ul>
 *   <li>Auto-filter queries on entities annotated with {@code @TenantId}</li>
 *   <li>Auto-populate {@code @TenantId} columns on INSERT</li>
 * </ul>
 *
 * <h3>Root tenant trick</h3>
 * Hibernate 6.6 ({@code AbstractSharedSessionContract.setUpMultitenancy}) throws
 * {@code "SessionFactory configured for multi-tenancy, but no tenant identifier
 * specified"} the instant {@link #resolveCurrentTenantIdentifier()} returns
 * {@code null}, <em>before</em> it ever consults {@link #isRoot(UUID)}. That
 * makes the natural "return null when there is no tenant" pattern unusable
 * during application startup, in CLI commands and in {@link org.springframework.data.jpa.repository.JpaRepository}
 * bootstrap (Spring Data introspects each repository by opening a transient
 * {@code EntityManager}, which goes through the same code path).
 * <p>
 * Workaround: always return a sentinel UUID — {@link #ROOT_TENANT} — when the
 * thread-local context is empty, and declare it as a "root" tenant via
 * {@link #isRoot(UUID)}. Hibernate then takes the bypass branch and opens the
 * session without any {@code @TenantId} filter. The sentinel never matches a
 * real tenant id (it's the nil UUID) so no legitimate data leaks.
 */
public class TenantIdResolver implements CurrentTenantIdentifierResolver<UUID> {

	/**
	 * Sentinel tenant id used during application bootstrap, scheduled jobs,
	 * Flyway migrations and any other code path executed without a request.
	 * The nil UUID is guaranteed never to collide with a real tenant id
	 * (real tenants use UUIDv7 — version nibble {@code 7}).
	 */
	public static final UUID ROOT_TENANT = new UUID(0L, 0L);

	/**
	 * Sentinel tenant UUID for the {@code edushift-system} tenant that the
	 * {@link com.edushift.modules.auth.entity.UserRole#SUPER_ADMIN} role is
	 * bound to. Sprint 15 / BE-15.1.
	 *
	 * <p>This UUID differs from {@link #ROOT_TENANT} by one unit so that:
	 * <ul>
	 *   <li>{@code ROOT_TENANT} (nil UUID) continues to serve as the
	 *       Hibernate bootstrap sentinel — never matches a real row.</li>
	 *   <li>The actual {@code tenants} row for {@code edushift-system} lives
	 *       at {@code 00000000-0000-0000-0000-000000000001} and is a real
	 *       tenant with {@code slug = 'edushift-system'}.</li>
	 * </ul>
	 */
	public static final UUID SUPER_ADMIN_SENTINEL =
			UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		return TenantContext.current().orElse(ROOT_TENANT);
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

	@Override
	public boolean isRoot(UUID tenantIdentifier) {
		return tenantIdentifier == null
				|| ROOT_TENANT.equals(tenantIdentifier)
				|| SUPER_ADMIN_SENTINEL.equals(tenantIdentifier);
	}

}
