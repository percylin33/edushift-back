package com.edushift.modules.students.provision;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Builds hybrid login emails for CRED-7 mass provision.
 * Synthetic domain: {@code @slud-del-colegio.com}
 */
public final class SyntheticEmailFactory {

	public static final String SYNTHETIC_DOMAIN = "slud-del-colegio.com";

	private SyntheticEmailFactory() {}

	public static boolean isSyntheticAddress(String email) {
		if (email == null) {
			return false;
		}
		String lower = email.trim().toLowerCase(Locale.ROOT);
		return lower.endsWith(".accounts.edushift.local")
				|| lower.matches("[a-z0-9]+\\.[a-z0-9]+\\.[a-z0-9]+\\.[a-f0-9]{6,8}@[a-z0-9-]+\\.com");
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
	 * {@code nombre.apellido.documentNumber.sufijo@slud-del-colegio.com}
	 */
	public static String generate(
			String lastName,
			String documentNumber,
			String tenantSlug,
			Predicate<String> emailTaken
	) {
		return generate("user", lastName, documentNumber, tenantSlug, emailTaken);
	}

	public static String generate(
			String firstName,
			String lastName,
			String documentNumber,
			String tenantSlug,
			Predicate<String> emailTaken
	) {
		String name = normalizeToken(firstName);
		if (name.isBlank()) name = "user";
		String surname = normalizeToken(lastName);
		if (surname.isBlank()) surname = "user";
		String doc = normalizeToken(documentNumber);
		if (doc.isBlank()) doc = "x";
		name = name.substring(0, Math.min(name.length(), 14));
		surname = surname.substring(0, Math.min(surname.length(), 14));
		doc = doc.substring(0, Math.min(doc.length(), 14));
		for (int attempt = 0; attempt < 32; attempt++) {
			String hex = String.format(Locale.ROOT, "%06x", ThreadLocalRandom.current().nextInt(0x1000000));
			String candidate = name + "." + surname + "." + doc + "." + hex + "@" + SYNTHETIC_DOMAIN;
			if (!emailTaken.test(candidate)) return candidate;
		}
		String fallback = name + "." + surname + "." + doc + "."
				+ UUID.randomUUID().toString().replace("-", "").substring(0, 8)
				+ "@" + SYNTHETIC_DOMAIN;
		return fallback.length() <= 254 ? fallback : fallback.substring(0, 254);
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
