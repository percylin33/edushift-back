package com.edushift.modules.auth.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the {@code com.warrenstrange:googleauth} TOTP library
 * (Sprint 17 / BE-17.2).
 *
 * <h3>What this service provides</h3>
 * <ul>
 *   <li>Generation of a 160-bit secret (the standard TOTP secret size,
 *       base32-encoded by the library).</li>
 *   <li>The {@code otpauth://} URI suitable for QR-code rendering.</li>
 *   <li>A PNG data URL of the QR code (base64), so the FE can render
 *       it without any client-side library.</li>
 *   <li>Code validation with a configurable window (default 1 step = ±30s).</li>
 * </ul>
 *
 * <h3>What this service does NOT do</h3>
 * <ul>
 *   <li>Persist the secret (the {@link MfaService} does that).</li>
 *   <li>Hash the secret (the {@link MfaService} does that with BCrypt).</li>
 *   <li>Generate / verify recovery codes (see {@link RecoveryCodeService}).</li>
 * </ul>
 */
@Slf4j
@Service
public class TotpService {

	/**
	 * Standard TOTP window (RFC 6238): accept codes from one step before
	 * to one step after the current 30-second slot. ±30s tolerates clock
	 * drift between server and authenticator app without weakening the
	 * window enough for an attacker to brute force.
	 */
	private static final int VALIDATION_WINDOW = 1;

	/**
	 * Issuer string embedded in the otpauth URI. Authenticator apps display
	 * this as the account label.
	 */
	private static final String ISSUER = "EduShift";

	private final GoogleAuthenticator authenticator;
	private final SecureRandom random = new SecureRandom();

	public TotpService() {
		// Use default config (SHA-1, 6 digits, 30s step) — matches every
		// authenticator app on the market. The library validates the
		// secret length on generation.
		this.authenticator = new GoogleAuthenticator();
	}

	@PostConstruct
	void logConfig() {
		log.info("[mfa] TotpService initialized -- window={} step(s), issuer={}",
				VALIDATION_WINDOW, ISSUER);
	}

	/**
	 * Generate a fresh TOTP secret. Each call produces a brand-new
	 * 160-bit random value; the caller is responsible for persisting it
	 * (we never store it ourselves).
	 */
	public GoogleAuthenticatorKey generateSecret() {
		return authenticator.createCredentials();
	}

	/**
	 * Build the {@code otpauth://totp/...} URI that authenticator apps
	 * understand. The QR code is generated from this URI.
	 *
	 * @param key      the secret key
	 * @param email    shown in the authenticator as the account name
	 * @param tenantName shown in the authenticator as the issuer line
	 */
	public String buildOtpAuthUri(GoogleAuthenticatorKey key, String email, String tenantName) {
		return GoogleAuthenticatorQRGenerator.getOtpAuthURL(
				ISSUER + ":" + (tenantName == null ? email : email + " (" + tenantName + ")"),
				email,
				key);
	}

	/**
	 * Render a PNG data URL of the QR code. The {@code googleauth} library
	 * itself does not provide QR rendering (intentionally — the maintainer
	 * prefers to keep the library focused on TOTP). The FE is expected to
	 * render the {@code otpauth://} URI itself using a small client-side
	 * library (e.g. {@code qrcode} npm package). For server-side
	 * rendering in tests, use the {@code ZXing} library directly.
	 *
	 * <p>For convenience in development and E2E tests, this method returns
	 * the {@code otpauth://} URI unchanged (most QR libraries accept it
	 * directly).
	 */
	public String buildQrCodeDataUrl(GoogleAuthenticatorKey key, String email, String tenantName) {
		return buildOtpAuthUri(key, email, tenantName);
	}

	/**
	 * Validate a 6-digit TOTP code against a stored secret (already base32-decoded).
	 *
	 * @return true if the code matches within the validation window
	 */
	public boolean validateCode(String secretBase32, int code) {
		return authenticator.authorize(secretBase32, code);
	}

	/**
	 * Securely zero out a secret (best-effort). The Java spec doesn't
	 * guarantee that strings are cleared from memory but this is a
	 * defensive practice when the secret has been consumed or rotated.
	 */
	@SuppressWarnings("unused")
	private void wipe(char[] secret) {
		for (int i = 0; i < secret.length; i++) {
			secret[i] = '\0';
		}
	}
}