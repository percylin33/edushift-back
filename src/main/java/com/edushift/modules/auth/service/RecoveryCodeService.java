package com.edushift.modules.auth.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Generation and verification of one-time recovery codes for MFA
 * (Sprint 17 / BE-17.2).
 *
 * <h3>Why recovery codes</h3>
 * A user who loses their authenticator app is otherwise locked out of
 * their account. Recovery codes are the standard escape hatch: 10 codes
 * are shown to the user once at enrollment; each code can be used exactly
 * once to bypass MFA. We hash them with BCrypt before persistence, so a
 * DB leak does not expose them.
 *
 * <h3>Format</h3>
 * 10-character alphanumeric (uppercase, no ambiguous characters: 0/O/1/I).
 * Grouped as {@code XXXXX-XXXXX} for readability when displayed.
 */
@Slf4j
@Service
public class RecoveryCodeService {

	/** Standard OWASP-recommended count. */
	public static final int RECOVERY_CODE_COUNT = 10;

	/** Length of each code, excluding the dash. */
	public static final int RECOVERY_CODE_LENGTH = 10;

	/**
	 * Alphabet excluding ambiguous characters (0/O, 1/I/L). 32 characters
	 * gives ~5 bits per char; 10 chars = ~50 bits per code, ~166 bits
	 * across 10 codes (well above the 128-bit security floor).
	 */
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private static final int GROUP_SIZE = 5;

	private final SecureRandom random = new SecureRandom();
	private final PasswordEncoder passwordEncoder;

	public RecoveryCodeService(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Generate a fresh set of recovery codes. Returns the plaintext list
	 * (to display to the user) and the hashed list (to persist in the DB).
	 *
	 * @return pair of (plaintext codes shown to the user, BCrypt hashes
	 *         to persist in {@code mfa_recovery_codes_hash})
	 */
	public GeneratedCodes generate(int count) {
		List<String> plaintext = new ArrayList<>(count);
		List<String> hashed = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String code = randomCode();
			plaintext.add(code);
			hashed.add(passwordEncoder.encode(code));
		}
		return new GeneratedCodes(plaintext, hashed);
	}

	/**
	 * Check a candidate code against the stored hashed list. The first
	 * matching code is "consumed" (replaced with null in the list) so
	 * the next call must use a different code. Returns the index of
	 * the consumed code, or -1 if no match.
	 *
	 * <p>Mutates {@code hashedCodes} in place; the caller is expected to
	 * persist the list back.
	 */
	public int verifyAndConsume(List<String> hashedCodes, String candidate) {
		if (candidate == null || candidate.isBlank() || hashedCodes == null) {
			return -1;
		}
		String normalized = candidate.trim().toUpperCase().replace("-", "");
		for (int i = 0; i < hashedCodes.size(); i++) {
			String hash = hashedCodes.get(i);
			if (hash == null) {
				continue; // already consumed
			}
			if (passwordEncoder.matches(normalized, hash)) {
				hashedCodes.set(i, null);
				return i;
			}
		}
		return -1;
	}

	/** Generates a single 10-char code in the form {@code XXXXX-XXXXX}. */
	private String randomCode() {
		StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH + 1);
		for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
			if (i == GROUP_SIZE) {
				sb.append('-');
			}
			sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}

	/** Plaintext + hashed pair returned by {@link #generate(int)}. */
	public record GeneratedCodes(List<String> plaintext, List<String> hashed) {}
}