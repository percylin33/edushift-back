package com.edushift.modules.auth.listener;

import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.events.UserStatusChangeEvent;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to <strong>{@link UserStatusChangeEvent}</strong> and revokes
 * every active refresh token for the affected user.
 *
 * <p>Closes <strong>DEBT-AUTH-4</strong>: when an admin suspends /
 * disables / locks a user, the next {@code /refresh} call from that user
 * must fail with 401 (we revoke everything in the chain with reason
 * {@code ADMIN_REVOKE}).
 *
 * <h3>Why {@code AFTER_COMMIT}</h3>
 * We only act after the user update has been committed — otherwise a
 * failed transaction would leave tokens revoked but the user status
 * reverted to ACTIVE, causing the next login to look "valid" while
 * tokens are useless.
 *
 * <h3>Scope: refresh tokens only</h3>
 * JWT access tokens live in the client until their TTL expires (15 min
 * in production). Tracking and revoking them across instances would
 * require a Redis-backed blocklist — registered as
 * <em>DEBT-AUTH-BLOCKLIST</em>. For "user suspended" workflows a 15-minute
 * window is acceptable; for emergency revocations we still have the
 * {@code /auth/logout} endpoint.
 *
 * <h3>Idempotency</h3>
 * Re-revoking an already-revoked refresh token is a no-op (the SQL
 * UPDATE has the {@code revoked_at IS NULL} guard).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusChangeListener {

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final AuditLogger auditLogger;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onUserStatusChange(UserStatusChangeEvent event) {
		User user = userRepository.findByPublicUuid(event.userPublicUuid()).orElse(null);
		if (user == null) {
			log.warn("[auth] UserStatusChangeEvent ignored — user not found: {}",
					event.userPublicUuid());
			return;
		}

		// No-op transitions: ACTIVE→ACTIVE, or any transition that doesn't
		// actually break authentication (e.g. ACTIVE→LOCKED when LOCKED is
		// already block — but we still revoke because LOCKED is terminal).
		if (event.oldStatus() == event.newStatus()) {
			return;
		}

		// Only block transitions actually block authentication.
		boolean oldCanAuth = event.oldStatus() != null && event.oldStatus().canAuthenticate();
		boolean newCanAuth = event.newStatus() != null && event.newStatus().canAuthenticate();
		if (oldCanAuth && !newCanAuth) {
			int revoked = revokeAllFor(user);
			log.info("[auth] DEBT-AUTH-4: status change {} → {} revoked {} refresh token(s) for user={} reason='{}'",
					event.oldStatus(), event.newStatus(), revoked, user.getPublicUuid(), event.reason());

			if (revoked > 0) {
				auditLogger.log(AuditAction.ADMIN_REVOKE, "refresh_token", null,
						"Revoked " + revoked + " refresh tokens due to user status change: "
								+ event.oldStatus() + " → " + event.newStatus()
								+ " (reason: " + event.reason() + ")",
						java.util.Map.of(
								"userPublicUuid", user.getPublicUuid().toString(),
								"fromStatus", event.oldStatus().name(),
								"toStatus", event.newStatus().name(),
								"actorPublicUuid", String.valueOf(event.actorPublicUuid()),
								"reason", event.reason() == null ? "" : event.reason(),
								"revokedCount", revoked
						));
			}
		}
	}

	/**
	 * Revoke every non-revoked refresh token for the given user. Returns
	 * the number of rows updated. Runs inside a {@code REQUIRES_NEW}
	 * transaction so it sees the freshly committed state.
	 */
	private int revokeAllFor(User user) {
		// TenantContext must be set so @TenantId filters correctly.
		UUID tenantId = user.getTenantId();
		if (tenantId == null) {
			return 0;
		}

		final int[] count = new int[] { 0 };
		TenantContext.runAs(tenantId, () -> {
			count[0] = refreshTokenRepository.revokeAllByUser(
					user.getId(),
					RevocationReason.ADMIN_REVOKE);
			return null;
		});
		return count[0];
	}
}
