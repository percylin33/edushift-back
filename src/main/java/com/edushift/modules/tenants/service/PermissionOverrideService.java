package com.edushift.modules.tenants.service;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.dto.RolePermissionOverrideDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for per-tenant role permission overrides (D1 / F0.5,
 * QA plan 2026-07-02 — see {@code docs/qa/12-custom-permissions-feature.md}).
 *
 * <h3>What this service exposes</h3>
 * <ul>
 *   <li>{@link #snapshotFor(UUID)} — fast read path used by
 *       {@code LmsRoleAuthorityMapper} on every JWT minting.</li>
 *   <li>{@link #listAll(UUID)} / {@link #upsert(UUID, UserRole, String, boolean, UUID)}
 *       — write paths used by the {@code /v1/tenants/me/permission-overrides}
 *       endpoint.</li>
 *   <li>The service itself does NOT include the platform default
 *       {@code LMS_*} set. That lives in
 *       {@code LmsRoleAuthorityMapper.DEFAULTS}; this service only
 *       layers OVER it. See
 *       {@code LmsRoleAuthorityMapper.resolveFor(tenantId, roles)}.</li>
 * </ul>
 *
 * <h3>Caching</h3>
 * Snapshots are cached for {@value #CACHE_TTL_SECONDS} seconds per
 * tenant and evicted on every {@link #upsert}. The cache is in-memory
 * keyed by tenant UUID — bounded by tenant count, safe across the
 * tenants we ship today (O(100) entries at scale). If we ever need
 * cross-instance consistency (multi-instance BE behind LB), this should
 * move to Redis.
 */
public interface PermissionOverrideService {

	int CACHE_TTL_SECONDS = 300;

	/**
	 * Read-only snapshot of the active overrides for a tenant. Returns
	 * the matrix as a {@code Map<role, authorities>} where
	 * {@code authorities} is the EXPLICITLY granted set (granted=false
	 * rows are stored separately — see implementation). For the
	 * mapper's full resolution, see
	 * {@code LmsRoleAuthorityMapper.resolveFor}.
	 */
	Map<UserRole, Set<String>> snapshotFor(UUID tenantId);

	/**
	 * Read-only snapshot of AUTHORITIES explicitly REVOKED for each role
	 * (i.e. rows where {@code granted = false}). Used by the mapper to
	 * subtract from the platform default.
	 */
	Map<UserRole, Set<String>> revokedFor(UUID tenantId);

	/**
	 * Lists all active overrides for the tenant (UI list view). Does
	 * NOT include platform defaults.
	 */
	List<RolePermissionOverrideDto> listAll(UUID tenantId);

	/**
	 * Atomically:
	 * <ol>
	 *   <li>Looks up the (tenant, role, authority) triple.</li>
	 *   <li>If a row exists → updates {@code granted} + {@code grantedByUserId}.</li>
	 *   <li>If no row exists → inserts a new one.</li>
	 *   <li>Evicts the cache for the tenant.</li>
	 * </ol>
	 * Soft-delete is reserved for the "reset to defaults" flow
	 * (separate endpoint, future sprint).
	 */
	RolePermissionOverrideDto upsert(UUID tenantId, UserRole role, String authority, boolean granted, UUID actorUserId);

	/** Drops every cached snapshot for the tenant — called on writes. */
	void evict(UUID tenantId);
}

