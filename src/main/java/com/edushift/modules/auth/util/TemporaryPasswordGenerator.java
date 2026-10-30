package com.edushift.modules.auth.util;

import java.security.SecureRandom;

/**
 * One-time temporary passwords for CRED-7 mass provision and admin
 * {@code TEMPORARY} password reset. Only the BCrypt hash is persisted;
 * plaintext is returned once to the caller.
 */
public final class TemporaryPasswordGenerator {

	private static final char[] ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
	private static final int LENGTH = 14;
	private static final SecureRandom RANDOM = new SecureRandom();

	private TemporaryPasswordGenerator() {}

	public static String generate() {
		char[] chars = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
		}
		return new String(chars);
	}
}
