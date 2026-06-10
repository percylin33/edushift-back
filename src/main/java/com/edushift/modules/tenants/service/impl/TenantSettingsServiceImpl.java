package com.edushift.modules.tenants.service.impl;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.tenants.service.TenantSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link TenantSettingsService} backed by a small in-memory TTL
 * cache (Sprint 6 / BE-6.2.A).
 *
 * <h3>Why a hand-rolled cache?</h3>
 * Spring Cache + Redis would also work, but for a single read-heavy
 * setting with 60s TTL the operational cost (a Redis round trip per
 * cache hit + serialization) outweighs the in-memory variant. The map
 * is bounded by tenant count (we expect O(100) entries even at scale)
 * and resets on app restart, which is acceptable since the worst-case
 * staleness is 60s anyway.
 *
 * <p>If we ever need cross-instance consistency (e.g. real-time tenant
 * setting flips), we'll move this to Redis with the same TTL.
 */
@Slf4j
@Service
public class TenantSettingsServiceImpl implements TenantSettingsService {

	private static final String SETTINGS_ATTENDANCE_KEY = "attendance";
	private static final String SETTING_LATE_AFTER_MINUTES_KEY = "lateAfterMinutes";

	private final TenantRepository tenantRepository;
	private final int defaultLateAfterMinutes;
	private final Duration cacheTtl;
	private final ConcurrentHashMap<UUID, CachedSettings> cache = new ConcurrentHashMap<>();

	public TenantSettingsServiceImpl(
			TenantRepository tenantRepository,
			@Value("${edushift.attendance.late-after-default-minutes:10}")
					int defaultLateAfterMinutes,
			@Value("${edushift.attendance.settings-cache-ttl-seconds:60}")
					long cacheTtlSeconds) {
		this.tenantRepository = tenantRepository;
		this.defaultLateAfterMinutes = sanitizeNonNegative(defaultLateAfterMinutes);
		this.cacheTtl = Duration.ofSeconds(Math.max(cacheTtlSeconds, 1));
	}

	@Override
	@Transactional(readOnly = true)
	public int getLateAfterMinutes(UUID tenantId) {
		if (tenantId == null) {
			return defaultLateAfterMinutes;
		}
		Instant now = Instant.now();
		CachedSettings cached = cache.get(tenantId);
		if (cached != null && cached.expiresAt().isAfter(now)) {
			return cached.lateAfterMinutes();
		}
		int resolved = resolveFromDb(tenantId);
		cache.put(tenantId, new CachedSettings(resolved, now.plus(cacheTtl)));
		return resolved;
	}

	private int resolveFromDb(UUID tenantId) {
		// Bypass the @TenantId discriminator: lateAfterMinutes can be
		// requested for the *current* tenant, but the cache is keyed by
		// the literal UUID — using findById ensures we always read the
		// row even if the TenantContext disagrees with the cache key.
		return tenantRepository.findById(tenantId)
				.map(this::extractLateAfterMinutes)
				.orElse(defaultLateAfterMinutes);
	}

	private int extractLateAfterMinutes(Tenant tenant) {
		Map<String, Object> settings = tenant.getSettings();
		if (settings == null || settings.isEmpty()) {
			return defaultLateAfterMinutes;
		}
		Object attendanceNode = settings.get(SETTINGS_ATTENDANCE_KEY);
		if (!(attendanceNode instanceof Map<?, ?> attendanceMap)) {
			return defaultLateAfterMinutes;
		}
		Object value = attendanceMap.get(SETTING_LATE_AFTER_MINUTES_KEY);
		Integer parsed = coerceToNonNegativeInt(value);
		if (parsed == null) {
			if (value != null) {
				log.warn("[tenant-settings] tenantId={} has malformed "
						+ "settings.attendance.lateAfterMinutes='{}'; "
						+ "falling back to default {}",
						tenant.getId(), value, defaultLateAfterMinutes);
			}
			return defaultLateAfterMinutes;
		}
		return parsed;
	}

	private static Integer coerceToNonNegativeInt(Object value) {
		if (value == null) return null;
		try {
			int parsed = switch (value) {
				case Number n -> n.intValue();
				case String s -> Integer.parseInt(s.trim());
				default -> Integer.parseInt(value.toString().trim());
			};
			return parsed < 0 ? null : parsed;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static int sanitizeNonNegative(int value) {
		return Math.max(value, 0);
	}

	private record CachedSettings(int lateAfterMinutes, Instant expiresAt) {}
}
