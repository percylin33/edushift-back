package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.dto.RegisterDeviceRequest;
import com.edushift.modules.notifications.entity.UserDeviceToken;
import com.edushift.modules.notifications.repository.UserDeviceTokenRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM device token registry (Sprint cierre-C / B8).
 *
 * <p>Multi-tenant safety: the {@code userPublicUuid} captured on
 * register is the JWT's user id (always tenant-scoped via the token's
 * tenant_id claim). Cross-tenant writes are impossible because the
 * row's tenant_id is taken from {@link TenantContext} and the user
 * lookup is tenant-bound.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

	private final UserDeviceTokenRepository repository;
	private final CurrentUserProvider currentUserProvider;

	@Transactional
	public void register(RegisterDeviceRequest req) {
		UUID tenantId = TenantContext.currentRequired();
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user required to register a device"));

		UserDeviceToken existing = repository.findByToken(req.token()).orElse(null);
		if (existing != null) {
			// Token may have rotated between users (e.g. shared device logged out
			// and a different user logged in). Take it over.
			existing.setTenantId(tenantId);
			existing.setUserPublicUuid(userId);
			existing.setPlatform(req.platform());
			existing.setActive(true);
			existing.setUnregisteredAt(null);
			existing.setLastSeenAt(Instant.now());
			repository.save(existing);
			log.info("[fcm-device] token re-registered (rotated/overtook) tenant={} user={}",
					tenantId, userId);
			return;
		}

		UserDeviceToken token = new UserDeviceToken();
		token.setTenantId(tenantId);
		token.setUserPublicUuid(userId);
		token.setToken(req.token());
		token.setPlatform(req.platform());
		token.setActive(true);
		token.setLastSeenAt(Instant.now());
		repository.save(token);
		log.info("[fcm-device] token registered tenant={} user={} platform={}",
				tenantId, userId, req.platform());
	}

	@Transactional
	public void heartbeat(String token) {
		UserDeviceToken existing = repository.findByToken(token).orElse(null);
		if (existing == null) {
			// Stale heartbeat from an unregistered token - ignore.
			log.debug("[fcm-device] heartbeat on unknown token (no-op)");
			return;
		}
		existing.setLastSeenAt(Instant.now());
		if (!existing.isActive()) {
			// Heartbeat on a deactivated token: revive it (user re-installed).
			existing.setActive(true);
			existing.setUnregisteredAt(null);
		}
		repository.save(existing);
	}

	@Transactional
	public void unregister(String token) {
		UserDeviceToken existing = repository.findByToken(token).orElse(null);
		if (existing == null) return;
		existing.setActive(false);
		existing.setUnregisteredAt(Instant.now());
		repository.save(existing);
		log.info("[fcm-device] token unregistered user={}", existing.getUserPublicUuid());
	}

	@Transactional(readOnly = true)
	public List<UserDeviceToken> findActiveTokensFor(UUID userPublicUuid) {
		UUID tenantId = TenantContext.currentRequired();
		return repository
				.findByTenantIdAndUserPublicUuidAndActiveTrueOrderByLastSeenAtDesc(tenantId, userPublicUuid);
	}
}