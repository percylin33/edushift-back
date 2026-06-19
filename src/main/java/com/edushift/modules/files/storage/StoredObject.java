package com.edushift.modules.files.storage;

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

	/**
	 * Builds the standard remote key for a tenant-scoped upload:
	 * {@code tenants/{tenantId}/lms/{module}/{publicUuid}}.
	 *
	 * <p>Using the public UUID (not the internal id) keeps the key
	 * stable across DB migrations that swap the id generator, and
	 * means a leaked remote key is no more sensitive than the
	 * public UUID itself.
	 */
	public static String buildKey(UUID tenantId, String module, UUID publicUuid, String extension) {
		String safeExt = (extension == null || extension.isBlank())
				? ""
				: "." + extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		return "tenants/%s/lms/%s/%s%s".formatted(tenantId, module, publicUuid, safeExt);
	}
}
