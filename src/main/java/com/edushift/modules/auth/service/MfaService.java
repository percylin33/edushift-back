package com.edushift.modules.auth.service;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.UnauthorizedException;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MFA (TOTP) lifecycle (Sprint 17 / BE-17.2).
 *
 * <h3>Public API</h3>
 * <ul>
 *   <li>{@link #startEnrollment} — generate a fresh secret + QR code.</li>
 *   <li>{@link #verifyEnrollment} — confirm the user can produce a
 *       valid TOTP code from the new secret; persists the secret and
 *       the recovery codes; flips {@code user.mfaEnabled=true}.</li>
 *   <li>{@link #disable} — clear MFA state after proving knowledge of
 *       both the password and a current TOTP code.</li>
 *   <li>{@link #verifyChallenge} — used by the login flow when the user
 *       already has MFA enabled. Accepts a TOTP code OR a recovery code.</li>
 *   <li>{@link #regenerateRecoveryCodes} — invalidate all current
 *       recovery codes and emit a fresh set.</li>
 * </ul>
 *
 * <h3>Storage of the TOTP secret</h3>
 * TOTP validation requires the server to know the plaintext secret so
 * it can generate the expected code for the current 30-second window
 * and compare. Asymmetric hashes (BCrypt) are not viable for this —
 * we need reversible storage. The {@code mfa_secret_hash} column stores
 * the base32 secret <em>as-is</em>.
 *
 * <p>The name of the column is misleading for legacy reasons (the
 * original DEBT-AUTH-7 reserved the name expecting BCrypt, but TOTP
 * changed the requirements). A production deployment MUST enable
 * column-level encryption at rest (e.g. Postgres {@code pgcrypto}
 * with a KMS-managed key) so a DB dump alone does not leak usable
 * TOTP secrets. This MVP relies on filesystem + backup encryption
 * instead; the gap is documented in {@code auth.md §MFA}.
 *
 * <h3>Storage of recovery codes</h3>
 * Recovery codes ARE hashed with BCrypt — they are one-time secrets
 * that the user can choose to write down. A DB leak must not reveal
 * them in usable form.
 */
@Slf4j
@Service
public class MfaService {

	/** Stable code returned when the challenge succeeds. */
	public static final String RESULT_OK = "OK";

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final TotpService totpService;
	private final RecoveryCodeService recoveryCodeService;
	private final PasswordEncoder passwordEncoder;

	public MfaService(UserRepository userRepository,
	                  TenantRepository tenantRepository,
	                  TotpService totpService,
	                  RecoveryCodeService recoveryCodeService,
	                  PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.totpService = totpService;
		this.recoveryCodeService = recoveryCodeService;
		this.passwordEncoder = passwordEncoder;
	}

	// ------------------------------------------------------------------------
	// Enrollment
	// ------------------------------------------------------------------------

	/**
	 * Step 1: generate a fresh TOTP secret and a QR code data URL.
	 * <strong>The plaintext secret is returned to the FE so it can render
	 * the QR.</strong> The server-side {@code User} row is NOT updated yet —
	 * the secret is only persisted on successful {@link #verifyEnrollment}.
	 */
	@Transactional(readOnly = true)
	public EnrollmentStart startEnrollment(UUID userPublicUuid) {
		User user = lookupUser(userPublicUuid);
		if (user.isMfaEnabled()) {
			throw new BadRequestException("MFA_ALREADY_ENABLED",
					"MFA is already enabled for this user; disable it first to re-enroll");
		}
		GoogleAuthenticatorKey key = totpService.generateSecret();
		String qrDataUrl = totpService.buildQrCodeDataUrl(
				key, user.getEmail(), tenantRepository.findById(user.getTenantId())
						.map(Tenant::getName).orElse(null));
		// We keep the plaintext secret in memory for the duration of the
		// enroll handshake; the FE echoes it back in /enroll/verify so the
		// server can confirm the user has scanned the QR before persisting.
		return new EnrollmentStart(key.getKey(), qrDataUrl, otpauthUri(user, key));
	}

	/**
	 * Step 2: verify the first TOTP code from the user's authenticator.
	 * If valid, persist the (plaintext) secret and BCrypt-hashed recovery
	 * codes, and flip {@code mfaEnabled=true}.
	 *
	 * @return the plaintext recovery codes — these are shown to the user
	 *         ONE time and never again. Storing the hash; lose the
	 *         plaintext and the user has to re-enroll.
	 */
	@Transactional
	public List<String> verifyEnrollment(UUID userPublicUuid, String secretBase32, int totpCode) {
		User user = lookupUser(userPublicUuid);
		if (!totpService.validateCode(secretBase32, totpCode)) {
			throw new UnauthorizedException("INVALID_TOTP_CODE",
					"The TOTP code does not match; please re-scan the QR and try again");
		}
		// Persist the TOTP secret (plaintext) and the recovery code hashes.
		// The column name `mfa_secret_hash` is a misnomer; see class javadoc.
		user.setMfaSecretHash(secretBase32);
		RecoveryCodeService.GeneratedCodes codes = recoveryCodeService.generate(
				RecoveryCodeService.RECOVERY_CODE_COUNT);
		user.setMfaRecoveryCodesHash(new ArrayList<>(codes.hashed()));
		user.setMfaEnrolledAt(Instant.now());
		user.setMfaEnabled(true);
		userRepository.saveAndFlush(user);

		log.info("[mfa] enrollment verified -- userId={}", user.getId());
		return codes.plaintext();
	}

	// ------------------------------------------------------------------------
	// Login challenge
	// ------------------------------------------------------------------------

	/**
	 * Verify a TOTP or recovery code presented during the MFA step of
	 * the login flow. Used by the {@code AuthService} after a successful
	 * password check has produced an {@code mfaToken}.
	 *
	 * @return {@link #RESULT_OK} on success
	 * @throws UnauthorizedException with a stable code on failure
	 */
	@Transactional
	public String verifyChallenge(UUID userPublicUuid, String code) {
		User user = lookupUser(userPublicUuid);
		if (!user.isMfaEnabled() || user.getMfaSecretHash() == null) {
			throw new UnauthorizedException("MFA_NOT_ENABLED",
					"MFA is not enabled for this user");
		}

		String trimmed = code == null ? "" : code.trim();
		if (trimmed.matches("^\\d{6}$")) {
			int numericCode = Integer.parseInt(trimmed);
			if (!totpService.validateCode(user.getMfaSecretHash(), numericCode)) {
				throw new UnauthorizedException("INVALID_TOTP_CODE",
						"The TOTP code is invalid or expired");
			}
			log.info("[mfa] TOTP verified -- userId={}", user.getId());
			return RESULT_OK;
		}

		// Recovery code path
		int consumed = recoveryCodeService.verifyAndConsume(
				user.getMfaRecoveryCodesHash(), trimmed);
		if (consumed < 0) {
			throw new UnauthorizedException("INVALID_MFA_CODE",
					"The MFA code is invalid");
		}
		userRepository.saveAndFlush(user);
		log.info("[mfa] recovery code consumed -- userId={}, index={}", user.getId(), consumed);
		return RESULT_OK;
	}

	// ------------------------------------------------------------------------
	// Disable
	// ------------------------------------------------------------------------

	/**
	 * Disable MFA after the user proves identity via password + TOTP code
	 * (or recovery code). Clears all MFA state on the user row.
	 */
	@Transactional
	public void disable(UUID userPublicUuid, String currentPassword, String mfaCode) {
		User user = lookupUser(userPublicUuid);
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new UnauthorizedException("INVALID_PASSWORD",
					"Current password is incorrect");
		}
		if (!user.isMfaEnabled() || user.getMfaSecretHash() == null) {
			throw new BadRequestException("MFA_NOT_ENABLED",
					"MFA is not enabled for this user");
		}
		// Validate the MFA code (TOTP or recovery).
		String trimmed = mfaCode == null ? "" : mfaCode.trim();
		boolean valid = false;
		if (trimmed.matches("^\\d{6}$")) {
			valid = totpService.validateCode(user.getMfaSecretHash(),
					Integer.parseInt(trimmed));
		} else {
			valid = recoveryCodeService.verifyAndConsume(
					user.getMfaRecoveryCodesHash(), trimmed) >= 0;
		}
		if (!valid) {
			throw new UnauthorizedException("INVALID_MFA_CODE",
					"The MFA code is invalid");
		}
		// Clear MFA state
		user.setMfaSecretHash(null);
		user.setMfaRecoveryCodesHash(null);
		user.setMfaEnrolledAt(null);
		user.setMfaEnabled(false);
		userRepository.saveAndFlush(user);
		log.info("[mfa] MFA disabled -- userId={}", user.getId());
	}

	// ------------------------------------------------------------------------
	// Recovery codes regeneration
	// ------------------------------------------------------------------------

	@Transactional
	public List<String> regenerateRecoveryCodes(UUID userPublicUuid, String currentPassword) {
		User user = lookupUser(userPublicUuid);
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new UnauthorizedException("INVALID_PASSWORD",
					"Current password is incorrect");
		}
		if (!user.isMfaEnabled()) {
			throw new BadRequestException("MFA_NOT_ENABLED",
					"MFA is not enabled for this user");
		}
		RecoveryCodeService.GeneratedCodes codes = recoveryCodeService.generate(
				RecoveryCodeService.RECOVERY_CODE_COUNT);
		user.setMfaRecoveryCodesHash(new ArrayList<>(codes.hashed()));
		userRepository.saveAndFlush(user);
		log.info("[mfa] recovery codes regenerated -- userId={}", user.getId());
		return codes.plaintext();
	}

	// ------------------------------------------------------------------------
	// Internals
	// ------------------------------------------------------------------------

	private User lookupUser(UUID publicUuid) {
		Optional<User> opt = userRepository.findByPublicUuid(publicUuid);
		if (opt.isEmpty()) {
			throw new UnauthorizedException("USER_NOT_FOUND",
					"User not found");
		}
		return opt.get();
	}

	private String otpauthUri(User user, GoogleAuthenticatorKey key) {
		String tenantName = tenantRepository.findById(user.getTenantId())
				.map(Tenant::getName).orElse(null);
		return totpService.buildOtpAuthUri(key, user.getEmail(), tenantName);
	}

	/** Result of {@link #startEnrollment}. */
	public record EnrollmentStart(String secretBase32, String qrCodeDataUrl, String otpauthUri) {}
}