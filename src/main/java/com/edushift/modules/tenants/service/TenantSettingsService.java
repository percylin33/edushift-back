package com.edushift.modules.tenants.service;

import java.util.UUID;

/**
 * Read-only access to per-tenant settings stored in
 * {@link com.edushift.modules.tenants.entity.Tenant#getSettings()}
 * (Sprint 6 / BE-6.2.A).
 *
 * <h3>Why a dedicated service?</h3>
 * The attendance module needs to read
 * {@code tenant.settings.attendance.lateAfterMinutes} on every check-in
 * (hot path). Going through {@link com.edushift.modules.tenants.repository.TenantRepository}
 * would issue a SELECT per scan; a tiny TTL cache keeps the lookup
 * O(1) without invalidation complexity.
 *
 * <h3>Cache semantics (ADR-6.5)</h3>
 * <ul>
 *   <li>TTL 60s (configurable via
 *       {@code edushift.attendance.settings-cache-ttl-seconds}).</li>
 *   <li>Tenant updates that flip the value take effect on the next read
 *       after the TTL expires; "in-flight" sessions still use the value
 *       seen at scan time, which matches the documented behaviour
 *       in {@code attendance.md} REQ-ATT-02 edge case.</li>
 *   <li>Misses (no entry, malformed JSON, missing key) fall back to the
 *       configurable global default.</li>
 * </ul>
 */
public interface TenantSettingsService {

	int DEFAULT_UNIT_COUNT = 8;
	int MIN_UNIT_COUNT = 1;
	int MAX_UNIT_COUNT = 20;

	static int clampUnitCount(int count) {
		return Math.max(MIN_UNIT_COUNT, Math.min(MAX_UNIT_COUNT, count));
	}

	/**
	 * Resolve the {@code lateAfterMinutes} attendance setting for a
	 * tenant. Always non-negative.
	 *
	 * @param tenantId internal {@code tenants.id} (UUIDv7) — same value
	 *                 returned by
	 *                 {@link com.edushift.shared.security.CurrentUserProvider#currentTenantId()}.
	 * @return the configured value, or the global default if the tenant
	 *         did not set it (or the value is malformed).
	 */
	int getLateAfterMinutes(UUID tenantId);

	/**
	 * Resolve {@code settings.academic.defaultUnitCount} for a tenant.
	 * Clamped to 1..20; falls back to {@code 8} when missing or invalid.
	 */
	int getDefaultUnitCount(UUID tenantId);

	/**
	 * Persist {@code settings.academic.defaultUnitCount} (merge into the
	 * nested {@code academic} map; does not replace unrelated settings).
	 * Clamps to 1..20.
	 */
	void setDefaultUnitCount(UUID tenantId, int count);
}
