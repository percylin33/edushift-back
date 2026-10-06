package com.edushift.modules.tenants.service;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * L1 cache of {@link TenantStatus} for the JWT authentication hot path.
 *
 * <h3>Why</h3>
 * {@code JwtAuthenticationFilter} must reject tokens whose tenant is no
 * longer {@link TenantStatus#ACTIVE}. Without a cache that check hits
 * Postgres on <em>every</em> authenticated request and multiplies the
 * cost of morning peaks (attendance, LMS, {@code /me}).
 *
 * <h3>Staleness</h3>
 * TTL is 30 seconds. Suspend/restore via admin tooling becomes effective
 * within that window even if the write path forgets to
 * {@link #invalidate(UUID)}. Write paths that already change status
 * (e.g. {@code activateCurrent}) should call {@link #put} /
 * {@link #invalidate} so the next request sees the new value immediately.
 *
 * <h3>Missing tenant</h3>
 * Matches the historical filter behaviour: unknown {@code tenantId}
 * is treated as {@link TenantStatus#ACTIVE} (auth still fails later if
 * the principal cannot resolve a real tenant elsewhere).
 */
@Component
@RequiredArgsConstructor
public class TenantStatusCache {

	private static final int MAX_SIZE = 2_000;
	private static final Duration TTL = Duration.ofSeconds(30);

	private final TenantRepository tenantRepository;

	private final Cache<UUID, TenantStatus> cache = Caffeine.newBuilder()
			.maximumSize(MAX_SIZE)
			.expireAfterWrite(TTL)
			.build();

	/**
	 * Cached status lookup. Never returns {@code null}.
	 */
	public TenantStatus getStatus(UUID tenantId) {
		if (tenantId == null) {
			return TenantStatus.ACTIVE;
		}
		TenantStatus cached = cache.getIfPresent(tenantId);
		if (cached != null) {
			return cached;
		}
		TenantStatus status = tenantRepository.findById(tenantId)
				.map(Tenant::getStatus)
				.orElse(TenantStatus.ACTIVE);
		if (status == null) {
			status = TenantStatus.ACTIVE;
		}
		cache.put(tenantId, status);
		return status;
	}

	/** Seed / refresh an entry after a successful status write. */
	public void put(UUID tenantId, TenantStatus status) {
		if (tenantId == null || status == null) {
			return;
		}
		cache.put(tenantId, status);
	}

	/** Drop a single entry so the next read hits the DB. */
	public void invalidate(UUID tenantId) {
		if (tenantId == null) {
			return;
		}
		cache.invalidate(tenantId);
	}
}
