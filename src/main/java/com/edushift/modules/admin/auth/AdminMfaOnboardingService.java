package com.edushift.modules.admin.auth;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.admin.auth.AdminLoginResponse.AdminUserSummary;
import com.edushift.modules.admin.auth.dto.AdminDevMfaCompleteResponse;
import com.edushift.modules.admin.auth.dto.AdminDevMfaCompleteResponse.Bootstrap;
import com.edushift.modules.admin.auth.dto.AdminMfaRequiredResponse;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.auth.service.MfaService;
import com.edushift.modules.auth.service.MfaService.EnrollmentStart;
import com.edushift.modules.auth.service.RecoveryCodeService;
import com.edushift.modules.auth.service.TotpService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 15 / F-02 / H-02: SUPER_ADMIN-only MFA onboarding flow.
 *
 * <p>The {@link JwtService.TokenType#ONBOARDING ONBOARDING} JWT issued by
 * {@link AdminAuthService#login} is the only bearer accepted by these
 * endpoints. It is short-lived, single-purpose and cannot be swapped for
 * an ACCESS token anywhere else in the system.</p>
 *
 * <p>Flow:
 * <ol>
 *   <li>{@code POST /admin/mfa/enrol} → returns TOTP secret + QR + otpauth URI.</li>
 *   <li>{@code POST /admin/mfa/verify-enrol} → validates the first TOTP code,
 *       persists the secret + recovery codes, flips
 *       {@code user.mfaEnabled=true}, and returns a full access/refresh pair.</li>
 * </ol>
 *
 * <p>The dev-only {@code completeEnrolmentDev} method is invoked by the
 * profile-gated {@code AdminDevMfaController}. It skips the user-driven
 * TOTP code step (no authenticator app required in dev) but still
 * persists a real secret so subsequent logins follow the production
 * challenge flow unchanged.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMfaOnboardingService {

	private final JwtService jwtService;
	private final MfaService mfaService;
	private final TotpService totpService;
	private final RecoveryCodeService recoveryCodeService;
	private final UserRepository userRepository;
	private final AuditLogger auditLogger;
	private final PlatformTransactionManager txManager;

	public record EnrolmentPayload(
			String secretBase32,
			String qrCodeDataUrl,
			String otpauthUri,
			long expiresInSec
	) {}

	public EnrolmentPayload startEnrolment(String onboardingToken, String clientIp) {
		UUID userPublicUuid = requireOnboardingPrincipal(onboardingToken);
		TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () -> {
			new TransactionTemplate(txManager).executeWithoutResult(status -> {
				User user = userRepository.findByPublicUuid(userPublicUuid)
						.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
								"User not found"));
				if (user.isMfaEnabled()) {
					throw new BadRequestException("MFA_ALREADY_ENABLED",
							"MFA is already enabled");
				}
			});
			return null;
		});

		EnrollmentStart start = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> mfaService.startEnrollment(userPublicUuid));

		auditLogger.log(AuditAction.CONFIG_CHANGED, "admin_mfa",
				userPublicUuid,
				"SUPER_ADMIN MFA enrolment started",
				java.util.Map.of("clientIp", clientIp));
		return new EnrolmentPayload(
				start.secretBase32(),
				start.qrCodeDataUrl(),
				start.otpauthUri(),
				jwtService.onboardingTokenTtlSeconds());
	}

	public AdminAuthService.LoginOutcome verifyEnrolment(
			String onboardingToken, String secretBase32, int totpCode, String clientIp) {

		UUID userPublicUuid = requireOnboardingPrincipal(onboardingToken);

		List<String> recoveryCodes = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> mfaService.verifyEnrollment(userPublicUuid, secretBase32, totpCode));

		auditLogger.log(AuditAction.CONFIG_CHANGED, "admin_mfa",
				userPublicUuid,
				"SUPER_ADMIN MFA enrolment completed — recovery codes issued",
				java.util.Map.of("clientIp", clientIp,
						"recoveryCodesCount", recoveryCodes == null ? 0 : recoveryCodes.size()));

		return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> {
					User user = userRepository.findByPublicUuid(userPublicUuid)
							.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
									"User not found"));
					Tenant sentinel = new Tenant();
					sentinel.setId(TenantIdResolver.SUPER_ADMIN_SENTINEL);
					sentinel.setSlug("edushift-system");
					sentinel.setName("edushift-system");
					user.recordSuccessfulLogin();
					userRepository.saveAndFlush(user);
					String accessToken = jwtService.issueAccessToken(user, sentinel,
							user.getRoleNames());
					String refreshToken = jwtService.issueRefreshToken(user, sentinel);
					AdminUserSummary summary = new AdminUserSummary(
							user.getPublicUuid().toString(),
							user.getEmail(),
							user.getFirstName(),
							user.getLastName(),
							buildFullName(user),
							new java.util.ArrayList<>(user.getRoleNames()));
					log.info("[admin-auth] SUPER_ADMIN MFA onboarding complete -- publicUuid={}",
							user.getPublicUuid());
					return AdminLoginResponse.bearer(accessToken, refreshToken,
							jwtService.accessTokenTtlSeconds(), summary);
				});
	}

	private static String buildFullName(User user) {
		String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
		String last = user.getLastName() == null ? "" : user.getLastName().trim();
		String joined = (first + " " + last).trim();
		return joined.isEmpty() ? user.getEmail() : joined;
	}

	private UUID requireOnboardingPrincipal(String onboardingToken) {
		if (onboardingToken == null || onboardingToken.isBlank()) {
			throw new UnauthorizedException("ONBOARDING_TOKEN_MISSING",
					"Onboarding token is required");
		}
		JwtClaims claims;
		try {
			claims = jwtService.parseAndValidate(onboardingToken);
		}
		catch (RuntimeException e) {
			throw new UnauthorizedException("ONBOARDING_TOKEN_INVALID",
					"Onboarding token is invalid or expired");
		}
		if (claims.type() != JwtService.TokenType.ONBOARDING) {
			throw new UnauthorizedException("ONBOARDING_TOKEN_INVALID",
					"Token is not an onboarding token");
		}
		if (claims.tenantId() == null
				|| !TenantIdResolver.SUPER_ADMIN_SENTINEL.equals(claims.tenantId())) {
			throw new UnauthorizedException("ONBOARDING_TOKEN_INVALID",
					"Onboarding token tenant mismatch");
		}
		return UUID.fromString(claims.subject());
	}

	// ========================================================================
	// Dev-only MFA enrolment bypass (Sprint 15 / F-02 follow-up)
	// ========================================================================

	/**
	 * Result envelope for {@link #completeEnrolmentDev}. Plaintext session
	 * tokens + bootstrap material (raw TOTP secret, otpauth URI, recovery
	 * codes) are returned so an operator can pipe them into a real
	 * authenticator if parity testing is desired.
	 */
	public record DevEnrolmentResult(
			AdminLoginResponse session,
			Bootstrap bootstrap) {}

	/**
	 * Complete MFA enrolment for a SUPER_ADMIN that has an {@code ONBOARDING}
	 * bearer but no authenticator app on hand. Generates a fresh TOTP
	 * secret, persists it directly (skipping the user-typed code step),
	 * mints a real access/refresh pair, and audits the action with a
	 * {@code mode=DEV_BYPASS} marker.
	 *
	 * <p>This method is invoked ONLY by the profile-gated
	 * {@code AdminDevMfaController}; the service itself is not gated
	 * because the JWT principal check + the caller-side dev code check
	 * are both required before reaching here. In production builds the
	 * controller bean does not exist.</p>
	 *
	 * @param onboardingToken the {@code ONBOARDING} JWT from {@code /admin/login}
	 * @param devCode         the value of the {@code X-Dev-Code} request header
	 *                        (already compared in constant time by the caller)
	 * @param clientIp        forwarded IP for audit logging
	 * @return session + bootstrap material
	 */
	public DevEnrolmentResult completeEnrolmentDev(
			String onboardingToken, String devCode, String clientIp) {

		UUID userPublicUuid = requireOnboardingPrincipal(onboardingToken);

		// Everything inside the sentinel tenant context so Hibernate's
		// @TenantId filter targets the edushift-system tenant where the
		// SUPER_ADMIN rows live (TenantAwareEntity + discriminator).
		DevEnrolmentResult result = TenantContext.runAs(
				TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
						new TransactionTemplate(txManager).execute(status -> {

							User user = userRepository.findByPublicUuid(userPublicUuid)
									.orElseThrow(() -> new UnauthorizedException(
											"USER_NOT_FOUND", "User not found"));

							if (user.isMfaEnabled()) {
								throw new ConflictException("ALREADY_ENROLLED",
										"MFA is already enrolled for this user; "
												+ "use the standard /admin/mfa/challenge flow");
							}

							// 1) Generate a fresh TOTP secret + recovery codes.
							GoogleAuthenticatorKey key = totpService.generateSecret();
							RecoveryCodeService.GeneratedCodes codes = recoveryCodeService
									.generate(RecoveryCodeService.RECOVERY_CODE_COUNT);

							// 2) Persist directly — no human-typed code path in dev.
							user.setMfaSecretHash(key.getKey());
							user.setMfaRecoveryCodesHash(new ArrayList<>(codes.hashed()));
							user.setMfaEnrolledAt(Instant.now());
							user.setMfaEnabled(true);
							user.recordSuccessfulLogin();
							userRepository.saveAndFlush(user);

							// 3) Mint the real session.
					Tenant sentinel = new Tenant();
					sentinel.setId(TenantIdResolver.SUPER_ADMIN_SENTINEL);
					sentinel.setSlug("edushift-system");
					sentinel.setName("edushift-system");

					String accessToken = jwtService.issueAccessToken(
							user, sentinel, user.getRoleNames());
					String refreshToken = jwtService.issueRefreshToken(
							user, sentinel);

					AdminUserSummary summary = new AdminUserSummary(
							user.getPublicUuid().toString(),
							user.getEmail(),
							user.getFirstName(),
							user.getLastName(),
							buildFullName(user),
							new ArrayList<>(user.getRoleNames()));

					AdminLoginResponse session = AdminLoginResponse.bearer(
							accessToken, refreshToken,
							jwtService.accessTokenTtlSeconds(), summary);

					// 4) Audit with explicit DEV_BYPASS marker.
					auditLogger.log(AuditAction.CONFIG_CHANGED, "admin_mfa",
							user.getPublicUuid(),
							"SUPER_ADMIN MFA enrolment completed via DEV_BYPASS",
							java.util.Map.of(
									"mode", "DEV_BYPASS",
									"clientIp", clientIp != null ? clientIp : "unknown",
									"devCodeMatched", true,
									"recoveryCodesCount", codes.plaintext().size()));

					log.warn("[admin-auth] DEV_BYPASS MFA enrolment complete "
									+ "-- publicUuid={}, ip={}, recoveryCodes={}",
							user.getPublicUuid(), clientIp, codes.plaintext().size());

					String otpauthUri = totpService.buildOtpAuthUri(
							key, user.getEmail(), sentinel.getName());

							return new DevEnrolmentResult(session,
									new Bootstrap(key.getKey(), otpauthUri,
											List.copyOf(codes.plaintext())));
						}));

		return result;
	}
}
