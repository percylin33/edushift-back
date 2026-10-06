package com.edushift.modules.files.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound payload for {@link StorageService#put(StoragePutRequest)}
 * (Sprint 7a / BE-7a.0).
 *
 * <p>The caller (controller / service) supplies the user-facing metadata
 * ({@code originalName}, {@code contentType}) and the routing hint
 * ({@code module}); the provider is responsible for hashing the bytes,
 * computing a tenant-scoped {@code remoteKey}, persisting them, and
 * returning a {@link StoredObject} that records the key it chose.
 *
 * <p>The {@link InputStream} is consumed but never closed by the provider
 * — the caller owns the lifecycle. The provider must close any wrapper
 * streams it creates internally.
 */
public record StoragePutRequest(
		UUID tenantId,
		String tenantSlug,
		String module,
		UUID publicUuid,
		String originalName,
		String contentType,
		InputStream input,
		long declaredSizeBytes,
		String env,
		Instant uploadedAt
) {

	/**
	 * Convenience for callers that want a default key layout.
	 * Implementations use
	 * {@link StoredObject#buildKey(String, UUID, String, String, Instant, UUID, String)}.
	 */
	public String defaultRemoteKey(String extension) {
		return StoredObject.buildKey(
				env, tenantId, tenantSlug, module, uploadedAt, publicUuid, extension);
	}
}
