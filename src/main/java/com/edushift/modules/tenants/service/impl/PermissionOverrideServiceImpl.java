package com.edushift.modules.tenants.service.impl;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.dto.RolePermissionOverrideDto;
import com.edushift.modules.tenants.entity.RolePermissionOverride;
import com.edushift.modules.tenants.repository.RolePermissionOverrideRepository;
import com.edushift.modules.tenants.service.PermissionOverrideService;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link PermissionOverrideService} backed by JPA + an
 * in-memory TTL cache (D1 / F0.5, QA plan 2026-07-02).
 *
 * <h3>Cache semantics</h3>
 * One {@link CachedSnapshot} per tenant. Reads consult the cache first;
 * writes {@link #evict(UUID)} invalidate. The cache value stores both
 * the explicit-grants map and the explicit-revocations map so the
 * mapper can build its final authority set in one place.
 *
 * <h3>Cross-tenant safety</h3>
 * Every public method takes {@code tenantId} explicitly. The controller
 * never accepts {@code tenantId} from the request body — it always
 * comes from the authenticated session. The repository queries are
 * always rooted on the tenant UUID passed in, ignoring Hibernate's
 * {@code @TenantId} discriminator (since D1's whole point is to let a
 * tenant read ITS OWN overrides; the controller is the only legitimate
 * caller).
 */
@Slf4j
@Service
public class PermissionOverrideServiceImpl implements PermissionOverrideService {

	private final RolePermissionOverrideRepository repository;
	private final ConcurrentHashMap<UUID, CachedSnapshot> cache = new ConcurrentHashMap<>();
	private final Duration cacheTtl;

	public PermissionOverrideServiceImpl(
			RolePermissionOverrideRepository repository,
			@Value("${edushift.permissions.override-cache-ttl-seconds:300}")
					long cacheTtlSeconds
	) {
		this.repository = repository;
		this.cacheTtl = Duration.ofSeconds(Math.max(cacheTtlSeconds, 1));
	}

	@Override
	public Map<UserRole, Set<String>> snapshotFor(UUID tenantId) {
		CachedSnapshot s = readCacheOrLoad(tenantId);
		return s.granted;
	}

	@Override
	public Map<UserRole, Set<String>> revokedFor(UUID tenantId) {
		CachedSnapshot s = readCacheOrLoad(tenantId);
		return s.revoked;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RolePermissionOverrideDto> listAll(UUID tenantId) {
		return repository.findByTenantId(tenantId).stream()
				.map(RolePermissionOverrideDto::from)
				.toList();
	}

	@Override
	@Transactional
	public RolePermissionOverrideDto upsert(
			UUID tenantId,
			UserRole role,
			String authority,
			boolean granted,
			UUID actorUserId
	) {
		RolePermissionOverride row = repository
				.findByTenantIdAndRoleAndAuthority(tenantId, role, authority)
				.orElseGet(() -> {
					RolePermissionOverride fresh = new RolePermissionOverride();
					fresh.setTenantId(tenantId);
					fresh.setRole(role);
					fresh.setAuthority(authority);
					fresh.setGrantedByUserId(actorUserId);
					return fresh;
				});

		row.setGranted(granted);
		row.setGrantedByUserId(actorUserId);
		RolePermissionOverride saved = repository.save(row);

		// Invalidate BEFORE returning so the very next read sees the new
		// state. Cache eviction is best-effort (eviction races are fine —
		// a stale read for a few hundred ms is acceptable; an inconsistent
		// read for hours is not).
		evict(tenantId);

		log.info("[permissions] override upsert tenantId={} role={} authority={} granted={} actor={}",
				tenantId, role, authority, granted, actorUserId);
		return RolePermissionOverrideDto.from(saved);
	}

	@Override
	public void evict(UUID tenantId) {
		if (tenantId != null) {
			cache.remove(tenantId);
		}
	}

	private CachedSnapshot readCacheOrLoad(UUID tenantId) {
		if (tenantId == null) {
			return CachedSnapshot.EMPTY;
		}
		Instant now = Instant.now();
		CachedSnapshot hit = cache.get(tenantId);
		if (hit != null && hit.expiresAt.isAfter(now)) {
			return hit;
		}
		CachedSnapshot fresh = loadFromDb(tenantId);
		cache.put(tenantId, fresh);
		return fresh;
	}

	@Transactional(readOnly = true)
	protected CachedSnapshot loadFromDb(UUID tenantId) {
		Map<UserRole, Set<String>> granted = new EnumMap<>(UserRole.class);
		Map<UserRole, Set<String>> revoked = new EnumMap<>(UserRole.class);
		for (UserRole r : UserRole.values()) {
			if (r == UserRole.SUPER_ADMIN) continue;
			granted.put(r, new java.util.HashSet<>());
			revoked.put(r, new java.util.HashSet<>());
		}
		List<RolePermissionOverride> rows = repository.findByTenantId(tenantId);
		for (RolePermissionOverride o : rows) {
			UserRole r = o.getRole();
			if (r == UserRole.SUPER_ADMIN) continue;
			(ifThen((o.isGranted() ? granted : revoked), r)).add(o.getAuthority());
		}
		return new CachedSnapshot(granted, revoked, Instant.now().plus(cacheTtl));
	}

	@SuppressWarnings("unchecked")
	private static Set<String> ifThen(Map<UserRole, Set<String>> target, UserRole r) {
		return target.computeIfAbsent(r, k -> new java.util.HashSet<>());
	}

	/**
	 * Inline snapshot type to keep both the granted and the revoked maps
	 * in one cache entry. Avoids re-querying the DB twice on the
	 * mapper hot path.
	 */
	private record CachedSnapshot(
			Map<UserRole, Set<String>> granted,
			Map<UserRole, Set<String>> revoked,
			Instant expiresAt
	) {
		static final CachedSnapshot EMPTY = new CachedSnapshot(Map.of(), Map.of(), Instant.MAX);
	}
}
