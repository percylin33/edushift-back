package com.edushift.modules.academic.levelgrade.service;

import java.util.UUID;

/**
 * Seeds the default academic catalog (levels + grades) into a tenant
 * the first time it is created.
 *
 * <h3>Idempotency</h3>
 * The implementation is a no-op when the tenant already has at least
 * one academic level. This makes the call safe to re-run from migrations
 * or admin tools, and shields the {@code TenantServiceImpl.register}
 * happy path from races during startup.
 *
 * <h3>Tenant context</h3>
 * The caller MUST establish {@code TenantContext.runAs(tenantId, ...)}
 * before invoking {@link #seedDefaults(UUID)} — the service relies on
 * Hibernate's discriminator to populate {@code tenant_id} on every row.
 */
public interface AcademicSeedService {

	/**
	 * Inserts the default {@code AcademicLevel} + {@code Grade} catalog
	 * for the given tenant if (and only if) the tenant has no levels
	 * yet. Returns {@code true} when seeding ran, {@code false} when it
	 * was a no-op.
	 *
	 * <p>Must be called inside an active {@code TenantContext.runAs(tenantId, ...)}
	 * scope; the {@code tenantId} parameter is informational and is logged.</p>
	 */
	boolean seedDefaults(UUID tenantId);
}
