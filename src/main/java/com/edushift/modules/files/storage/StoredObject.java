package com.edushift.modules.files.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Result of a successful {@link StorageService#put} call (Sprint 7a / BE-7a.0).
 *
 * <p>The {@code provider} + {@code remoteKey} pair is persisted in
 * {@code lms_file_objects} and is the only handle needed to retrieve or
 * delete the binary afterwards.
 *
 * @param provider   the storage provider that accepted the bytes
 * @param remoteKey  the provider-side object key
 * @param bucket     the GCS bucket name (FIREBASE only); null for LOCAL_FS
 * @param sizeBytes  size of the persisted bytes (may differ from the input
 *                   stream length when the provider transparently re-encodes)
 * @param checksumSha256 lowercase hex SHA-256 of the persisted bytes
 */
public record StoredObject(
		StorageProvider provider,
		String remoteKey,
		String bucket,
		long sizeBytes,
		String checksumSha256
) {

	/** Allowed logical module buckets for object keys. */
	public static final Set<String> ALLOWED_MODULES = Set.of(
			"materials",
			"submissions",
			"avatars",
			"branding",
			"attendance",
			"schedule-bootstrap",
			"reports",
			"payments");


	private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy")
			.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM")
			.withZone(ZoneOffset.UTC);

	/**
	 * Builds the standard remote key for a tenant-scoped upload:
	 * {@code envs/{env}/tenants/{slug}-{tenantId}/{module}/{yyyy}/{MM}/{publicUuid}.ext}.
	 *
	 * <p>The tenant folder uses the school {@code slug} (URL-safe) plus the
	 * tenant UUID so the Firebase/GCS console is human-readable while
	 * remaining unique and multi-tenant safe. When {@code tenantSlug} is
	 * blank, falls back to {@code {tenantId}} alone (legacy-compatible).
	 */
	public static String buildKey(String env, UUID tenantId, String tenantSlug,
			String module, Instant uploadedAt, UUID publicUuid, String extension) {
		String safeEnv = sanitizeEnv(env);
		String safeModule = sanitizeModule(module);
		String tenantFolder = tenantFolder(tenantSlug, tenantId);
		Instant at = uploadedAt != null ? uploadedAt : Instant.now();
		String yyyy = YEAR.format(at);
		String mm = MONTH.format(at);
		String safeExt = (extension == null || extension.isBlank())
				? ""
				: "." + extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
		return "envs/%s/tenants/%s/%s/%s/%s/%s%s"
				.formatted(safeEnv, tenantFolder, safeModule, yyyy, mm, publicUuid, safeExt);
	}

	/**
	 * @deprecated Prefer {@link #buildKey(String, UUID, String, String, Instant, UUID, String)}.
	 *             Kept for transitional call sites; uses {@code env=dev} and {@link Instant#now()}.
	 */
	@Deprecated
	public static String buildKey(UUID tenantId, String module, UUID publicUuid, String extension) {
		return buildKey("dev", tenantId, null, module, Instant.now(), publicUuid, extension);
	}

	/**
	 * Tenant folder segment: {@code {slug}-{tenantId}} when slug is present,
	 * otherwise just {@code {tenantId}}.
	 */
	public static String tenantFolder(String tenantSlug, UUID tenantId) {
		String safe = sanitizeTenantSlug(tenantSlug);
		if (safe.isBlank()) {
			return tenantId.toString();
		}
		return safe + "-" + tenantId;
	}

	/**
	 * Lowercases and strips unsafe path characters from a tenant slug.
	 * Empty/null → empty string (caller falls back to UUID-only folder).
	 */
	public static String sanitizeTenantSlug(String slug) {
		if (slug == null || slug.isBlank()) {
			return "";
		}
		String normalized = slug.trim().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9_-]+", "-")
				.replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
		if (normalized.length() > 64) {
			normalized = normalized.substring(0, 64).replaceAll("-$", "");
		}
		return normalized;
	}

	public static boolean isAllowedModule(String module) {
		return module != null && ALLOWED_MODULES.contains(module.trim().toLowerCase(Locale.ROOT));
	}

	public static String requireAllowedModule(String module) {
		if (module == null || module.isBlank()) {
			throw new com.edushift.shared.exception.BadRequestException(
					"STORAGE_MODULE_REQUIRED",
					"Storage module is required");
		}
		String normalized = module.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_MODULES.contains(normalized)) {
			throw new com.edushift.shared.exception.BadRequestException(
					"STORAGE_MODULE_INVALID",
					"Storage module '" + module + "' is not allowed. Allowed: "
							+ ALLOWED_MODULES);
		}
		return normalized;
	}

	public static String sanitizeEnv(String env) {
		if (env == null || env.isBlank()) {
			return "dev";
		}
		String normalized = env.trim().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9_-]", "");
		if (normalized.isBlank()) {
			return "dev";
		}
		return switch (normalized) {
			case "production" -> "prod";
			case "development", "local", "test" -> "dev";
			default -> normalized;
		};
	}

	private static String sanitizeModule(String module) {
		return requireAllowedModule(module);
	}
}
