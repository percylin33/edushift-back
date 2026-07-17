package com.edushift.modules.admin.auth;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.infrastructure.ratelimit.SimpleRateLimiter;
import com.edushift.modules.admin.auth.AdminLoginResponse.AdminUserSummary;
import com.edushift.modules.admin.auth.dto.AdminMfaRequiredResponse;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.TooManyRequestsException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

	private static final int ADMIN_LOGIN_MAX_PER_MINUTE = 3;
	private static final int ADMIN_LOGIN_WINDOW_MS = 60_000;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuditLogger auditLogger;
	private final SimpleRateLimiter rateLimiter;
	private final PlatformTransactionManager txManager;
	private final RefreshTokenRepository refreshTokenRepository;

	/**
	 * Marker for the return of {@link #login}: either a full bearer session
	 * ({@link AdminLoginResponse}) or an onboarding handshake
	 * ({@link com.edushift.modules.admin.auth.dto.AdminMfaRequiredResponse}).
	 * Both records implement this marker so callers can branch on the
	 * concrete type without touching Spring ResponseEntity generics.
	 */
	public interface LoginOutcome {}

	public LoginOutcome login(AdminLoginRequest request, String clientIp) {
		String ipKey = "admin-login:" + clientIp;
		if (!rateLimiter.allow(ipKey, ADMIN_LOGIN_MAX_PER_MINUTE, ADMIN_LOGIN_WINDOW_MS)) {
			log.warn("[admin-auth] rate limited: email={}, ip={}", request.email(), clientIp);
			throw new TooManyRequestsException("TOO_MANY_LOGIN_ATTEMPTS",
					"Too many admin login attempts. Retry in 60 seconds.");
		}

		String normalizedEmail = request.email() == null
				? "" : request.email().trim().toLowerCase();

		UUID sentinelId = TenantIdResolver.SUPER_ADMIN_SENTINEL;

		return TenantContext.runAs(sentinelId, () -> {
			var result = new TransactionTemplate(txManager).execute(status -> {
				User user = userRepository.findByEmail(normalizedEmail).orElse(null);
				if (user == null) {
					log.info("[admin-auth] login failed (unknown email): ip={}", clientIp);
					auditLogger.log(AuditAction.LOGIN_FAILED, "admin", null,
							"Unknown SUPER_ADMIN email: " + normalizedEmail + " from " + clientIp);
					throw new UnauthorizedException("BAD_CREDENTIALS",
							"Invalid email or password");
				}

				// ============================================================
				// Sprint 15 / F-01 / H-01: reject seeded SUPER_ADMIN credentials
				// — the operator MUST run /admin/recover to bootstrap.
				// ============================================================
				if (com.edushift.infrastructure.seed.DevDataInitializer
						.isSeedPasswordResetSentinel(user.getPasswordHash())) {
					log.warn("[admin-auth] login blocked (seed sentinel): email={}, ip={}",
							normalizedEmail, clientIp);
					auditLogger.log(AuditAction.LOGIN_FAILED, "admin", user.getPublicUuid(),
							"Seed sentinel password presented for " + normalizedEmail
									+ " from " + clientIp);
					throw new UnauthorizedException("PASSWORD_RESET_REQUIRED",
							"Initial credentials have expired. Use POST /admin/recover to bootstrap.");
				}

				if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
					log.info("[admin-auth] login failed (bad password): email={}, ip={}",
							normalizedEmail, clientIp);
					auditLogger.log(AuditAction.LOGIN_FAILED, "admin", user.getPublicUuid(),
							"Bad password for " + normalizedEmail + " from " + clientIp);
					throw new UnauthorizedException("BAD_CREDENTIALS",
							"Invalid email or password");
				}

				if (!user.hasRole(UserRole.SUPER_ADMIN)) {
					log.warn("[admin-auth] login blocked (not SUPER_ADMIN): email={}, ip={}",
							normalizedEmail, clientIp);
					auditLogger.log(AuditAction.LOGIN_FAILED, "admin", user.getPublicUuid(),
							"Non-SUPER_ADMIN attempted admin login from " + clientIp);
					throw new ForbiddenException("FORBIDDEN_NOT_SUPER_ADMIN",
							"Only SUPER_ADMIN can use this endpoint");
				}

				if (user.getStatus() != com.edushift.modules.auth.entity.UserStatus.ACTIVE) {
					log.warn("[admin-auth] login blocked (inactive): email={}, status={}, ip={}",
							normalizedEmail, user.getStatus(), clientIp);
					auditLogger.log(AuditAction.LOGIN_FAILED, "admin", user.getPublicUuid(),
							"Inactive SUPER_ADMIN attempted login from " + clientIp);
					throw new ForbiddenException("FORBIDDEN_NOT_SUPER_ADMIN",
							"Account is not active");
				}

				Tenant sentinelTenant = new Tenant();
				sentinelTenant.setId(sentinelId);
				sentinelTenant.setSlug("edushift-system");
				sentinelTenant.setName("edushift-system");

				user.recordSuccessfulLogin();
				userRepository.saveAndFlush(user);

				// ============================================================
				// Sprint 15 / F-02 / H-02: MFA is mandatory for SUPER_ADMIN.
				// Pre-enrolment login returns an ONBOARDING token that only
				// the /admin/mfa/enrol + /admin/mfa/verify-enrol endpoints
				// accept. No access token is issued until MFA is enrolled.
				// ============================================================
				if (!user.isMfaEnabled() || user.getMfaSecretHash() == null) {
					String onboardingToken = jwtService.issueOnboardingToken(
							user, sentinelTenant);
					auditLogger.log(AuditAction.LOGIN, "admin", user.getPublicUuid(),
							"SUPER_ADMIN pre-MFA onboarding token issued: "
									+ normalizedEmail + " from " + clientIp);
					log.info("[admin-auth] SUPER_ADMIN onboarding token issued "
							+ "(MFA not enrolled): email={}, ip={}",
							normalizedEmail, clientIp);
					return AdminMfaRequiredResponse.onboarding(
							onboardingToken, jwtService.onboardingTokenTtlSeconds());
				}

				String accessToken = jwtService.issueAccessToken(
						user, sentinelTenant, user.getRoleNames());
				String refreshToken = jwtService.issueRefreshToken(user, sentinelTenant);

				AdminUserSummary summary = new AdminUserSummary(
						user.getPublicUuid().toString(),
						user.getEmail(),
						user.getFirstName(),
						user.getLastName(),
						buildFullName(user),
						new java.util.ArrayList<>(user.getRoleNames())
				);

				auditLogger.log(AuditAction.LOGIN, "admin", user.getPublicUuid(),
						"SUPER_ADMIN login: " + normalizedEmail + " from " + clientIp);

				log.info("[admin-auth] SUPER_ADMIN login OK: email={}, ip={}",
						normalizedEmail, clientIp);

				return AdminLoginResponse.bearer(
						accessToken, refreshToken,
						jwtService.accessTokenTtlSeconds(), summary);
			});
			return result;
		});
	}

	private static String buildFullName(User user) {
		String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
		String last = user.getLastName() == null ? "" : user.getLastName().trim();
		String joined = (first + " " + last).trim();
		return joined.isEmpty() ? user.getEmail() : joined;
	}

	public void logout(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return;
		}

		final UUID tenantId;
		try {
			JwtClaims claims = jwtService.parseAndValidate(rawRefreshToken);
			tenantId = claims.tenantId();
		}
		catch (RuntimeException e) {
			log.info("[admin-auth] logout received malformed token — no-op");
			return;
		}

		if (tenantId == null) {
			log.info("[admin-auth] logout token has no tenant_id — no-op");
			return;
		}

		final String tokenHash = sha256Hex(rawRefreshToken);

		TenantContext.runAs(tenantId, () -> {
			new TransactionTemplate(txManager).executeWithoutResult(status -> {
				var stored = refreshTokenRepository.findByTokenHash(tokenHash);
				if (stored.isEmpty()) {
					log.info("[admin-auth] logout token unknown — no-op");
					return;
				}
				var token = stored.get();
				if (token.isRevoked()) {
					log.info("[admin-auth] logout token already revoked — no-op");
					return;
				}
				token.revoke(com.edushift.modules.auth.entity.RevocationReason.LOGOUT);
				refreshTokenRepository.saveAndFlush(token);
				UUID actorPublicUuid = userRepository.findById(token.getUserId())
						.map(User::getPublicUuid).orElse(null);
				auditLogger.log(AuditAction.LOGOUT, "admin", actorPublicUuid,
						"SUPER_ADMIN logout OK",
						java.util.Map.of("refreshTokenId", token.getId()));
				log.info("[admin-auth] logout OK -- tokenId={}, userId={}",
						token.getId(), token.getUserId());
			});
			return null;
		});
	}

	private static String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
