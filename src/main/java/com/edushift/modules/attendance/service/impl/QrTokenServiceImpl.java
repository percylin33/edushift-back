package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.attendance.exception.QrInvalidException;
import com.edushift.modules.attendance.service.QrTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default {@link QrTokenService} implementation backed by a
 * cryptographically-random 12-character base32 short ID
 * (Sprint 6 / BE-6.2.B / ADR-6.1, revision after BE-6.8.b).
 *
 * <p>Why a short ID instead of a JWT? Density. A 250-char JWT forces
 * the QR into version 10+ (~57x57 modules) which scans poorly from
 * student credentials printed at credit-card size. A 12-char short ID
 * fits in QR version 1 (~21x21 modules) which scans reliably from
 * across the classroom. Security is preserved — the secret is the row
 * in {@code student_attendance_qr}, not the bytes on the credential.</p>
 *
 * <h3>Alphabet</h3>
 * Crockford's base32 minus {@code 0/O}, {@code 1/I/L} to avoid
 * OCR/handwriting ambiguity when a parent reads the printed credential
 * to a teacher over the phone. 30-char alphabet, 12 chars → ~58.8 bits
 * of entropy. Collision probability with a million issued tokens is
 * ~2^-37 (negligible) and the {@code uk_qr_token_hash} unique index
 * acts as the final safeguard with an auto-retry on collision.
 */
@Slf4j
@Service
public class QrTokenServiceImpl implements QrTokenService {

	private static final char[] ALPHABET =
			"23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
	private static final int TOKEN_LENGTH = 12;
	private static final Pattern TOKEN_PATTERN = Pattern.compile(
			"^[2-9A-HJKMNP-TV-Z]{" + TOKEN_LENGTH + "}$");
	private static final SecureRandom RNG = new SecureRandom();

	@Override
	public IssuedQrToken issue(UUID studentPublicUuid, UUID tenantId) {
		if (studentPublicUuid == null || tenantId == null) {
			throw new IllegalArgumentException(
					"studentPublicUuid and tenantId are required to issue a QR token");
		}
		String token = generateToken();
		return new IssuedQrToken(token, hash(token));
	}

	@Override
	public void validateFormat(String token) {
		if (token == null || token.isBlank()) {
			throw new QrInvalidException("QR token is missing");
		}
		String normalised = token.trim().toUpperCase();
		if (!TOKEN_PATTERN.matcher(normalised).matches()) {
			throw new QrInvalidException(
					"QR token does not match the expected " + TOKEN_LENGTH
							+ "-character alphabet");
		}
	}

	@Override
	public String hash(String token) {
		if (token == null) {
			throw new IllegalArgumentException("token is required");
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(
					token.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(
					"SHA-256 algorithm is not available", e);
		}
	}

	private String generateToken() {
		StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
		for (int i = 0; i < TOKEN_LENGTH; i++) {
			sb.append(ALPHABET[RNG.nextInt(ALPHABET.length)]);
		}
		return sb.toString();
	}
}
