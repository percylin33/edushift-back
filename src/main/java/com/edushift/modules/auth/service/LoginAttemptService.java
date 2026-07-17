package com.edushift.modules.auth.service;

import com.edushift.modules.auth.entity.FailedLoginAttempt;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.exception.UserLockedException;
import com.edushift.modules.auth.repository.FailedLoginAttemptRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14 (MVP Closure) / DEBT-AUTH-7 — failed-login lockout temporal.
 *
 * <p>Tracks consecutive failed login attempts per (tenant, email). After
 * {@link FailedLoginAttempt#MAX_ATTEMPTS_BEFORE_LOCK} attempts within
 * {@link FailedLoginAttempt#ATTEMPT_WINDOW}, the account is locked for
 * {@link FailedLoginAttempt#LOCK_DURATION}.
 *
 * <h3>Why a dedicated service (not a Spring Event listener)</h3>
 * The lockout check MUST run <em>synchronously</em> on every login attempt
 * — a listener would race with the password verification step and leave a
 * window where the user authenticates after their account should be
 * locked. The service is called directly from
 * {@code AuthServiceImpl.login}, no async.
 *
 * <h3>TENANT_ADMIN exemption</h3>
 * Tenants that pay for EduShift cannot lose access to the platform if a
 * {@code TENANT_ADMIN} trips the lock. We skip the counter entirely for
 * admins so a brute-force attack cannot lock the only admin out of the
 * tenant.
 *
 * <h3>What "consecutive" means</h3>
 * Consecutive failures within {@link FailedLoginAttempt#ATTEMPT_WINDOW}.
 * A successful login resets the counter (see
 * {@link #recordSuccessfulLogin}). Older rows naturally age out and a
 * cleanup job (DEBT-AUTH-7-CLEANUP) marks them EXPIRED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

	private final FailedLoginAttemptRepository attemptRepository;
	private final UserRepository userRepository;
	private final com.edushift.modules.tenants.repository.TenantRepository tenantRepository;

	/**
	 * Throws {@link UserLockedException} if the user is currently locked.
	 * Called by {@code AuthServiceImpl.login} immediately after the user
	 * is loaded and BEFORE password validation, so the bcrypt cost is
	 * not paid on a locked account. Admins are exempt — see class javadoc.
	 */
	public void assertNotLocked(User user) {
		if (user == null) {
			return;
		}
		Instant until = user.getTemporarilyLockedUntil();
		if (until != null && until.isAfter(Instant.now())
				&& !user.hasRole(com.edushift.modules.auth.entity.UserRole.TENANT_ADMIN)) {
			log.warn("[auth-lockout] login blocked -- user={} lockedUntil={}",
					user.getPublicUuid(), until);
			throw new UserLockedException(until);
		}
	}

	/**
	 * Records a failed login attempt. If the user is currently a
	 * {@code TENANT_ADMIN} we skip the counter entirely (it's a DOS risk
	 * to lock out the tenant's only admin via an external brute-force
	 * attack).
	 *
	 * @param publicUuid      internal-or-external UUID of the user (we
	 *                        may pass {@code null} when the user lookup
	 *                        failed — see notes in AuthServiceImpl).
	 * @param email           normalized email of the failed attempt.
	 */
	@Transactional
	public void recordFailure(String email) {
		if (email == null || email.isBlank()) {
			return;
		}
		UUID tenantId = TenantContext.current().orElse(null);
		if (tenantId == null) {
			log.debug("[auth-lockout] recordFailure skipped: no tenant context");
			return;
		}

		// Don't lock TENANT_ADMIN — see javadoc.
		var maybeUser = userRepository.findByEmail(email);
		if (maybeUser.isPresent()
				&& maybeUser.get().hasRole(com.edushift.modules.auth.entity.UserRole.TENANT_ADMIN)) {
			log.info("[auth-lockout] skip counter for admin user email={}", email);
			return;
		}

		FailedLoginAttempt row = attemptRepository.findMostRecent(email)
				.filter(r -> r.getStatus() == FailedLoginAttempt.Status.ACTIVE
						|| r.getStatus() == FailedLoginAttempt.Status.LOCKED)
				// Skip EXPIRED / CLEARED rows; create a fresh window.
				.filter(r -> r.getLastAttemptAt()
						.isAfter(Instant.now().minus(FailedLoginAttempt.ATTEMPT_WINDOW)))
				.orElseGet(() -> {
					FailedLoginAttempt fresh = new FailedLoginAttempt();
					fresh.setEmail(email);
					fresh.setFirstAttemptAt(Instant.now());
					fresh.setLastAttemptAt(Instant.now());
					fresh.setAttemptCount(0);
					fresh.setStatus(FailedLoginAttempt.Status.ACTIVE);
					// tenantSlug is NOT NULL on the table; copy from the current
					// tenant so the INSERT doesn't violate the 23502 constraint
					// on first-ever failures (the entity's @TenantId field is
					// auto-populated, but tenant_slug is the legacy column that
					// the schema still requires).
					String tenantSlug = tenantRepository.findById(tenantId)
							.map(com.edushift.modules.tenants.entity.Tenant::getSlug)
							.orElse(null);
					fresh.setTenantSlug(tenantSlug);
					return fresh;
				});

		row.increment();
		attemptRepository.saveAndFlush(row);

		if (row.getAttemptCount() >= FailedLoginAttempt.MAX_ATTEMPTS_BEFORE_LOCK) {
			applyLockoutToUser(email);
			row.applyLockout();
			attemptRepository.saveAndFlush(row);
			log.warn("[auth-lockout] ACCOUNT LOCKED -- email={} attempts={} until={}",
					email, row.getAttemptCount(), row.getLockedUntil());
		}
	}

	/**
	 * Called after a successful login to reset the counter.
	 */
	@Transactional
	public void recordSuccessfulLogin(String email) {
		if (email == null || email.isBlank()) {
			return;
		}
		var maybeRow = attemptRepository.findMostRecent(email);
		if (maybeRow.isEmpty()) {
			return;
		}
		FailedLoginAttempt row = maybeRow.get();
		if (row.getStatus() == FailedLoginAttempt.Status.ACTIVE
				|| row.getStatus() == FailedLoginAttempt.Status.LOCKED) {
			row.clear();
			attemptRepository.saveAndFlush(row);
			// Clear the user-side flag too.
			userRepository.findByEmail(email).ifPresent(u -> {
				u.setTemporarilyLockedUntil(null);
				userRepository.saveAndFlush(u);
			});
			log.info("[auth-lockout] counter cleared after success -- email={}", email);
		}
	}

	/**
	 * Writes {@code users.temporarily_locked_until = now() + LOCK_DURATION}.
	 * Idempotent: a user who is already locked has their until-time refreshed.
	 */
	private void applyLockoutToUser(String email) {
		userRepository.findByEmail(email).ifPresent(u -> {
			u.setTemporarilyLockedUntil(Instant.now().plus(FailedLoginAttempt.LOCK_DURATION));
			userRepository.saveAndFlush(u);
		});
	}

	/** Helper for callers wanting "remaining lock time". */
	public Duration remainingLockDuration(User user) {
		Instant until = user.getTemporarilyLockedUntil();
		if (until == null || !until.isAfter(Instant.now())) {
			return Duration.ZERO;
		}
		return Duration.between(Instant.now(), until);
	}
}
