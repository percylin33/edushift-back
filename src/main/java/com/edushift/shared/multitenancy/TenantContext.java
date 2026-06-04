package com.edushift.shared.multitenancy;

import com.edushift.shared.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-request holder of the current tenant id. Backed by a {@link ThreadLocal}.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>{@code TenantFilter} resolves the tenant at the start of the request and
 *       calls {@link #set(UUID)}</li>
 *   <li>{@code TenantInterceptor} refines / validates the tenant after Spring
 *       Security has populated the principal</li>
 *   <li>Hibernate reads it via {@code TenantIdResolver} to auto-filter queries
 *       and populate {@code @TenantId} columns on INSERT</li>
 *   <li>{@code TenantFilter#finally} calls {@link #clear()} so no value leaks
 *       across pooled threads</li>
 * </ol>
 */
public final class TenantContext {

	private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void set(UUID tenantId) {
		CURRENT.set(tenantId);
	}

	public static Optional<UUID> current() {
		return Optional.ofNullable(CURRENT.get());
	}

	/**
	 * Returns the current tenant id or throws when none is set.
	 *
	 * @throws BusinessException with code {@code TENANT_REQUIRED} when no tenant is bound
	 */
	public static UUID currentRequired() {
		UUID id = CURRENT.get();
		if (id == null) {
			throw new BusinessException("TENANT_REQUIRED",
					"Tenant context is required for this operation");
		}
		return id;
	}

	public static boolean isSet() {
		return CURRENT.get() != null;
	}

	public static void clear() {
		CURRENT.remove();
	}

	/**
	 * Executes the given action under the supplied tenant, restoring the previous
	 * binding afterwards. Useful for system jobs and cross-tenant tools.
	 */
	public static <T> T runAs(UUID tenantId, Supplier<T> action) {
		UUID previous = CURRENT.get();
		CURRENT.set(tenantId);
		try {
			return action.get();
		}
		finally {
			if (previous != null) {
				CURRENT.set(previous);
			}
			else {
				CURRENT.remove();
			}
		}
	}

}
