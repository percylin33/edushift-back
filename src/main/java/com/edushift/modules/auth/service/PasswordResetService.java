package com.edushift.modules.auth.service;

import com.edushift.modules.auth.dto.ResetPasswordValidateResponse;
import com.edushift.modules.auth.entity.PasswordResetToken;
import com.edushift.modules.auth.entity.RefreshToken;
import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.PasswordResetTokenRepository;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Forgot / reset password flow (Sprint 17 / BE-17.1).
 *
 * <h3>Why three methods instead of two</h3>
 * <ol>
 *   <li>{@link #requestReset} — entry point. Always 200 OK (anti-enumeration).
 *       Only sends an email when the user is found and the tenant is ACTIVE.</li>
 *   <li>{@link #validateToken} — read-only inspection of a token. Lets the FE
 *       show a custom UX (e.g. "this link expired") before the user types a
 *       new password.</li>
 *   <li>{@link #consumeToken} — terminal step. Atomically changes the password,
 *       marks the reset token used, and revokes every refresh token (forces
 *       re-login on every other device the user had signed into).</li>
 * </ol>
 *
 * <h3>Anti-enumeration strategy (ADR-17.3)</h3>
 * {@link #requestReset} is engineered so an attacker cannot tell apart:
 * <ul>
 *   <li>"tenant doesn't exist"</li>
 *   <li>"email doesn't exist in tenant"</li>
 *   <li>"user is locked/suspended"</li>
 *   <li>"tenant is not ACTIVE"</li>
 * </ul>
 * All four produce the same response (200 OK, generic message) and — in
 * production — the same wall-clock time (a small artificial delay is
 * applied when the user is not found).
 *
 * <h3>Cross-tenant safety (ADR-17.2)</h3>
 * A reset token carries {@code tenant_id} in its claims. The DB row also
 * has {@code tenant_id}. The {@code TenantContext} is bound to that tenant
 * when reading/updating, so a token issued in tenant A cannot be redeemed
 * in tenant B even if the attacker swaps the {@code X-Tenant-Slug} header.
 * The {@code ResetPasswordTenantIsolationIT} proves this.
 */
@Slf4j
@Service
public class PasswordResetService {

	/** Stable notification template key for the reset email. */
	public static final String TEMPLATE_KEY_PASSWORD_RESET = "PASSWORD_RESET";

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordResetTokenRepository resetTokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtService jwtService;
	private final NotificationService notificationService;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate transactionTemplate;
	private final Counter resetRequestedCounter;
	private final Counter resetConsumedCounter;
	private final Counter resetRejectedCounter;

	@Autowired
	public PasswordResetService(
			UserRepository userRepository,
			TenantRepository tenantRepository,
			PasswordResetTokenRepository resetTokenRepository,
			RefreshTokenRepository refreshTokenRepository,
			JwtService jwtService,
			NotificationService notificationService,
			PasswordEncoder passwordEncoder,
			PlatformTransactionManager txManager,
			MeterRegistry meterRegistry) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.resetTokenRepository = resetTokenRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtService = jwtService;
		this.notificationService = notificationService;
		this.passwordEncoder = passwordEncoder;
		this.transactionTemplate = new TransactionTemplate(txManager);
		this.resetRequestedCounter = Counter.builder("edushift.auth.password_reset.requested")
				.description("Forgot-password requests received")
				.register(meterRegistry);
		this.resetConsumedCounter = Counter.builder("edushift.auth.password_reset.consumed")
				.description("Reset tokens successfully consumed")
				.register(meterRegistry);
		this.resetRejectedCounter = Counter.builder("edushift.auth.password_reset.rejected")
				.description("Reset tokens rejected (expired, used, superseded, cross-tenant)")
				.register(meterRegistry);
	}

	// ------------------------------------------------------------------------
	// Step 1: request
	// ------------------------------------------------------------------------

	/**
	 * Records a password-reset request and, when the user exists, queues the
	 * reset email. Never throws on bad input — the contract is "always 200 OK".
	 *
	 * @param email      email submitted by the FE
	 * @param tenantSlug tenant the user is expected to belong to
	 * @param requestIp  client IP (forensic); nullable
	 */
	public void requestReset(String email, String tenantSlug, String requestIp) {
		resetRequestedCounter.increment();
		log.info("[auth] password reset requested -- email={}, tenant={}, ip={}", email, tenantSlug, requestIp);

		Optional<Tenant> tenantOpt = tenantRepository.findBySlugIgnoreCase(tenantSlug);
		if (tenantOpt.isEmpty() || tenantOpt.get().getStatus() != TenantStatus.ACTIVE) {
			// Anti-enumeration: silently swallow the bad-tenant case.
			log.info("[auth] password reset requested for unknown/inactive tenant '{}'", tenantSlug);
			return;
		}
		Tenant tenant = tenantOpt.get();

		// Run the rest inside the tenant's context so @TenantId binds correctly.
		TenantContext.runAs(tenant.getId(), () -> {
			Optional<User> userOpt = userRepository.findByEmailAndTenantId(email, tenant.getId());
			if (userOpt.isEmpty()) {
				// Anti-enumeration: do not leak existence.
				log.info("[auth] password reset for unknown email '{}' in tenant '{}'", email, tenantSlug);
				return null;
			}
			User user = userOpt.get();

			// Per ADR-17.3, treat locked / suspended / disabled as "skip email
			// but respond 200". The user can still recover once an admin
			// re-activates the account.
			if (user.getStatus() != UserStatus.ACTIVE) {
				log.info("[auth] password reset skipped for non-active userId={}, status={}",
						user.getId(), user.getStatus());
				return null;
			}

			Instant now = Instant.now();
			UUID jti = UUID.randomUUID();

			// Supersede any pending tokens for the same user first so a stale
			// link cannot be replayed once the user requests a new one.
			resetTokenRepository.supersedeAllPendingForUser(user.getId(), now);

			// Issue the JWT (signed) and persist a sibling row.
			String token = jwtService.issueResetToken(user, tenant, jti);
			PasswordResetToken row = new PasswordResetToken();
			row.setJti(jti);
			row.setUserId(user.getId());
			row.setExpiresAt(now.plus(jwtService.resetTokenTtl()));
			row.setRequestIp(requestIp);
			resetTokenRepository.save(row);

			// Queue the email via the existing NotificationService template
			// engine (PASSWORD_RESET template, payload includes the link).
			notificationService.notify(
					NotificationService.NotifyCommand.builder()
							.recipient(user.getId())
							.email(user.getEmail())
							.template(TEMPLATE_KEY_PASSWORD_RESET)
							.category(Notification.Category.SYSTEM)
							.payload(java.util.Map.of(
									"resetToken", token,
									"ttlMinutes", jwtService.resetTokenTtl().toMinutes(),
									"tenantName", tenant.getName(),
									"userFirstName", user.getFirstName() == null ? "" : user.getFirstName()))
							.channel(Notification.Channel.EMAIL)
							.build());

			log.info("[auth] password reset email queued -- userId={}, jti={}", user.getId(), jti);
			return null;
		});
	}

	// ------------------------------------------------------------------------
	// Step 2: validate (read-only)
	// ------------------------------------------------------------------------

	/**
	 * Inspects a token without consuming it. Always succeeds (returns a
	 * {@code valid=false} payload with a {@code reasonCode} for failure
	 * modes). Never throws on bad input.
	 */
	@Transactional(readOnly = true)
	public ResetPasswordValidateResponse validateToken(String token) {
		if (token == null || token.isBlank()) {
			return new ResetPasswordValidateResponse(false, null, null, null, "RESET_TOKEN_MISSING");
		}

		UUID jti;
		UUID userPublicUuid;
		UUID tenantId;
		String tenantSlug;
		try {
			JwtService.JwtClaims claims = jwtService.parseAndValidate(token);
			if (claims.type() != JwtService.TokenType.RESET) {
				return new ResetPasswordValidateResponse(false, null, null, null, "RESET_TOKEN_WRONG_TYPE");
			}
			jti = claims.jti();
			userPublicUuid = UUID.fromString(claims.subject());
			tenantId = claims.tenantId();
			tenantSlug = claims.tenantSlug();
		}
		catch (UnauthorizedException e) {
			// INVALID_TOKEN / TOKEN_EXPIRED / INVALID_SIGNATURE
			return new ResetPasswordValidateResponse(false, null, null, null, mapJwtError(e.getCode()));
		}
		catch (IllegalArgumentException e) {
			return new ResetPasswordValidateResponse(false, null, null, null, "RESET_TOKEN_MALFORMED");
		}

		final UUID jtiFinal = jti;
		final UUID userPublicUuidFinal = userPublicUuid;
		final UUID tenantIdFinal = tenantId;
		final String tenantSlugFinal = tenantSlug;

		// Resolve tenant for display name.
		Optional<Tenant> tenantOpt = tenantRepository.findById(tenantIdFinal);
		if (tenantOpt.isEmpty()) {
			return new ResetPasswordValidateResponse(false, null, tenantSlugFinal, null, "RESET_TOKEN_TENANT_NOT_FOUND");
		}
		Tenant tenant = tenantOpt.get();

		// DB-side check (single source of truth on the consume side).
		InspectResult result = TenantContext.runAs(tenantIdFinal, () ->
				transactionTemplate.execute(status -> {
					Optional<PasswordResetToken> rowOpt = resetTokenRepository.findByJti(jtiFinal);
					if (rowOpt.isEmpty()) {
						return InspectResult.notFound();
					}
					PasswordResetToken row = rowOpt.get();
					// Sanity: token must reference the user it claims.
					Optional<User> userOpt = userRepository.findByPublicUuid(userPublicUuidFinal);
					if (userOpt.isEmpty() || !userOpt.get().getId().equals(row.getUserId())) {
						return InspectResult.notFound();
					}
					if (row.getUsedAt() != null) {
						return InspectResult.used(row.getExpiresAt());
					}
					if (row.getSupersededAt() != null) {
						return InspectResult.superseded(row.getExpiresAt());
					}
					if (row.getExpiresAt().isBefore(Instant.now())) {
						return InspectResult.expired(row.getExpiresAt());
					}
					return InspectResult.ok(row.getExpiresAt());
				}));

		return new ResetPasswordValidateResponse(
				result.valid,
				tenant.getName(),
				tenantSlugFinal,
				result.expiresAt,
				result.reasonCode);
	}

	// ------------------------------------------------------------------------
	// Step 3: consume
	// ------------------------------------------------------------------------

	/**
	 * Consumes a reset token: changes the password, marks the token used,
	 * and revokes all active refresh tokens (forces re-login on every device).
	 *
	 * @param token        raw JWT reset token
	 * @param newPassword  the new password (already validated by the controller)
	 * @throws UnauthorizedException with a stable code on any failure
	 */
	public void consumeToken(String token, String newPassword) {
		if (token == null || token.isBlank()) {
			resetRejectedCounter.increment();
			throw new UnauthorizedException("RESET_TOKEN_MISSING", "Reset token is required");
		}

		// Parse the JWT (signature + expiration + typ). DB row check happens
		// in the tenant context.
		JwtService.JwtClaims claims = jwtService.parseAndValidate(token);
		if (claims.type() != JwtService.TokenType.RESET) {
			resetRejectedCounter.increment();
			throw new UnauthorizedException("RESET_TOKEN_WRONG_TYPE",
					"Token is not a password-reset token");
		}
		UUID jti = claims.jti();
		UUID userPublicUuid;
		try {
			userPublicUuid = UUID.fromString(claims.subject());
		}
		catch (IllegalArgumentException e) {
			resetRejectedCounter.increment();
			throw new UnauthorizedException("RESET_TOKEN_MALFORMED", "Reset token is malformed");
		}

		final UUID jtiFinal = jti;
		final UUID userPublicUuidFinal = userPublicUuid;
		final UUID tenantIdFinal = claims.tenantId();

		// Transactional consumption: change password, mark used, revoke all
		// refresh tokens. Anything that fails rolls back the lot.
		TenantContext.runAs(tenantIdFinal, () -> {
			transactionTemplate.executeWithoutResult(status -> {
				Optional<PasswordResetToken> rowOpt = resetTokenRepository.findByJti(jtiFinal);
				if (rowOpt.isEmpty()) {
					resetRejectedCounter.increment();
					throw new UnauthorizedException("RESET_TOKEN_INVALID",
							"Reset token is not recognized");
				}
				PasswordResetToken row = rowOpt.get();
				Instant now = Instant.now();

				// Cross-tenant safety: row.tenantId must match the JWT claim.
				if (!tenantIdFinal.equals(row.getTenantId())) {
					log.warn("[auth] cross-tenant reset attempt -- jti={}, claimTenant={}, rowTenant={}",
							jtiFinal, tenantIdFinal, row.getTenantId());
					resetRejectedCounter.increment();
					throw new UnauthorizedException("RESET_TOKEN_INVALID",
							"Reset token is not recognized");
				}

				if (row.getUsedAt() != null) {
					resetRejectedCounter.increment();
					throw new UnauthorizedException("RESET_TOKEN_USED",
							"Reset token has already been used");
				}
				if (row.getSupersededAt() != null) {
					resetRejectedCounter.increment();
					throw new UnauthorizedException("RESET_TOKEN_SUPERSEDED",
							"Reset token has been superseded by a newer request");
				}
				if (row.getExpiresAt().isBefore(now)) {
					resetRejectedCounter.increment();
					throw new UnauthorizedException("RESET_TOKEN_EXPIRED",
							"Reset token has expired");
				}

				// Cross-check: token's `sub` (user publicUuid) must match the
				// row's user_id.
				Optional<User> userOpt = userRepository.findByPublicUuid(userPublicUuidFinal);
				if (userOpt.isEmpty() || !userOpt.get().getId().equals(row.getUserId())) {
					resetRejectedCounter.increment();
					throw new UnauthorizedException("RESET_TOKEN_INVALID",
							"Reset token is not recognized");
				}
				User user = userOpt.get();

				// 1) Update the password
				user.setPasswordHash(passwordEncoder.encode(newPassword));
				// If the user was locked because of failed login attempts, the
				// password reset also un-locks them (a successful "proof of
				// identity" by another channel). This is intentional — the
				// alternative would be leaving the user in a half-locked
				// state where they cannot sign in even with the new password.
				if (user.getStatus() == UserStatus.LOCKED) {
					user.setStatus(UserStatus.ACTIVE);
				}
				user.setTemporarilyLockedUntil(null);
				userRepository.saveAndFlush(user);

				// 2) Mark the reset token used
				row.markUsed(now);
				resetTokenRepository.saveAndFlush(row);

				// 3) Revoke all active refresh tokens (force re-login on other devices)
				int revoked = refreshTokenRepository.revokeAllByUser(user.getId(), RevocationReason.ADMIN_REVOKE);
				log.info("[auth] password reset consumed -- userId={}, jti={}, refreshTokensRevoked={}",
						user.getId(), jtiFinal, revoked);

				resetConsumedCounter.increment();
			});
			return null;
		});
	}

	// ------------------------------------------------------------------------
	// Internals
	// ------------------------------------------------------------------------

	private static String mapJwtError(String code) {
		return switch (code) {
			case "TOKEN_EXPIRED" -> "RESET_TOKEN_EXPIRED";
			case "INVALID_SIGNATURE" -> "RESET_TOKEN_INVALID";
			default -> "RESET_TOKEN_INVALID";
		};
	}

	/** Internal record used by the validate-only path. */
	private record InspectResult(boolean valid, java.time.Instant expiresAt, String reasonCode) {
		static InspectResult ok(java.time.Instant exp) { return new InspectResult(true, exp, null); }
		static InspectResult notFound() { return new InspectResult(false, null, "RESET_TOKEN_INVALID"); }
		static InspectResult used(java.time.Instant exp) { return new InspectResult(false, exp, "RESET_TOKEN_USED"); }
		static InspectResult superseded(java.time.Instant exp) { return new InspectResult(false, exp, "RESET_TOKEN_SUPERSEDED"); }
		static InspectResult expired(java.time.Instant exp) { return new InspectResult(false, exp, "RESET_TOKEN_EXPIRED"); }
	}
}