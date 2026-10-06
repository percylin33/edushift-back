package com.edushift.modules.students.provision;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Builds hybrid login emails for CRED-7 mass provision.
 * Synthetic domain: {@code @{tenantSlug}.accounts.edushift.local}
 */
public final class SyntheticEmailFactory {

	public static final String SYNTHETIC_DOMAIN_SUFFIX = ".accounts.edushift.local";

	private SyntheticEmailFactory() {}

	public static boolean isSyntheticAddress(String email) {
		if (email == null) {
			return false;
		}
		String lower = email.trim().toLowerCase(Locale.ROOT);
		return lower.endsWith(SYNTHETIC_DOMAIN_SUFFIX);
	}

	public static boolean looksLikeRealEmail(String email) {
		if (email == null || email.isBlank()) {
			return false;
		}
		String lower = email.trim().toLowerCase(Locale.ROOT);
		if (isSyntheticAddress(lower)) {
			return false;
		}
		int at = lower.indexOf('@');
		return at > 0 && at < lower.length() - 1 && lower.indexOf('@', at + 1) < 0;
	}

	/**
	 * {@code apellido.documentNumber.4hex@tenantSlug.accounts.edushift.local}
	 */
	public static String generate(
			String lastName,
			String documentNumber,
			String tenantSlug,
			Predicate<String> emailTaken
	) {
		String slug = normalizeSlug(tenantSlug);
		String apellido = normalizeToken(lastName);
		if (apellido.isBlank()) {
			apellido = "user";
		}
		String doc = normalizeToken(documentNumber);
		if (doc.isBlank()) {
			doc = "x";
		}
		String domain = slug + SYNTHETIC_DOMAIN_SUFFIX;
		for (int attempt = 0; attempt < 32; attempt++) {
			String hex = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000))
					.toLowerCase(Locale.ROOT);
			while (hex.length() < 4) {
				hex = "0" + hex;
			}
			String candidate = apellido + "." + doc + "." + hex + "@" + domain;
			if (candidate.length() > 254) {
				candidate = truncateLocal(apellido, doc, hex) + "@" + domain;
			}
			if (!emailTaken.test(candidate)) {
				return candidate;
			}
		}
		// Extremely unlikely collision storm — fall back to UUID fragment.
		String fallback = apellido + "." + doc + "."
				+ UUID.randomUUID().toString().replace("-", "").substring(0, 8)
				+ "@" + domain;
		return fallback.length() <= 254 ? fallback : fallback.substring(0, 254);
	}

	private static String truncateLocal(String apellido, String doc, String hex) {
		String local = apellido + "." + doc + "." + hex;
		if (local.length() <= 64) {
			return local;
		}
		return local.substring(0, 64);
	}

	private static String normalizeSlug(String tenantSlug) {
		if (tenantSlug == null || tenantSlug.isBlank()) {
			return "tenant";
		}
		String s = tenantSlug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
		return s.isBlank() ? "tenant" : s;
	}

	private static String normalizeToken(String raw) {
		if (raw == null) {
			return "";
		}
		String nfd = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD);
		String noMarks = nfd.replaceAll("\\p{M}+", "");
		String alnum = noMarks.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
		return alnum;
	}
}
