package com.edushift.modules.attendance.service;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.security.CurrentUserProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tenant-scoped L1 cache for {@link User} lookups by {@code public_uuid}.
 *
 * <h3>Why</h3>
 * The attendance check-in hot path resolves the scanning user
 * ({@code scanned_by}) on every record creation to assemble the
 * response payload. During a morning peak the same auxiliary scans
 * 200+ QRs without their identity changing — caching the result
 * eliminates ~99% of those round-trips to Postgres.
 *
 * <h3>Tenant safety</h3>
 * The cache key is composite {@code (tenantId, userPublicUuid)}. Two
 * defenses against cross-tenant leak:
 * <ol>
 *   <li>{@code public_uuid} is UUID v7, globally unique across the
 *       entire database — a key collision across tenants is
 *       impossible by construction.</li>
 *   <li>Including the current {@code tenantId} in the key means that
 *       even if a future code path were to call this from outside its
 *       tenant context, no stale entry from another tenant could
 *       satisfy the lookup.</li>
 * </ol>
 *
 * <h3>Staleness</h3>
 * TTL is 5 minutes. The only data we read out of the cached entity in
 * the attendance flow is {@link User#fullName()} (firstName +
 * lastName); changes to those propagate within 5 min. Soft-delete or
 * status changes are not surfaced by attendance — if needed, callers
 * can call {@link #invalidate(UUID)} from user-management write paths.
 *
 * <h3>Bound</h3>
 * 500 entries max. Each scanning user across all active tenants is
 * separate, but 500 is more than enough for the next ~100 tenants
 * each with ~5 concurrent auxiliaries; LRU evicts the rest.
 */
@Component
@RequiredArgsConstructor
public class AttendanceUserCache {

	private static final int MAX_SIZE = 500;
	private static final Duration TTL = Duration.ofMinutes(5);

	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	private final Cache<CacheKey, User> cache = Caffeine.newBuilder()
			.maximumSize(MAX_SIZE)
			.expireAfterWrite(TTL)
			.build();

	private record CacheKey(UUID tenantId, UUID userPublicUuid) {}

	/**
	 * Tenant-scoped cached lookup. Cache miss falls through to
	 * {@link UserRepository#findByPublicUuid(UUID)} which is itself
	 * tenant-scoped via Hibernate's {@code @TenantId} filter.
	 *
	 * <p>Negative results (not-found) are NOT cached on purpose, so
	 * that a user freshly created during a peak becomes visible on
	 * the next scan without waiting for TTL.
	 */
	public Optional<User> findByPublicUuid(UUID publicUuid) {
		if (publicUuid == null) return Optional.empty();
		UUID tenantId = currentUserProvider.currentTenantId().orElse(null);
		if (tenantId == null) {
			// No tenant in context: fall back to the repository without
			// touching the cache (defense against bypassing tenant
			// scoping).
			return userRepository.findByPublicUuid(publicUuid);
		}
		CacheKey key = new CacheKey(tenantId, publicUuid);
		User cached = cache.getIfPresent(key);
		if (cached != null) return Optional.of(cached);
		Optional<User> fresh = userRepository.findByPublicUuid(publicUuid);
		fresh.ifPresent(u -> cache.put(key, u));
		return fresh;
	}

	/**
	 * Invalidate a single entry across all tenants. Cheap; iterates
	 * a 500-bound map. Called by user-management write paths.
	 */
	public void invalidate(UUID userPublicUuid) {
		if (userPublicUuid == null) return;
		cache.asMap().keySet().removeIf(k -> k.userPublicUuid().equals(userPublicUuid));
	}
}
