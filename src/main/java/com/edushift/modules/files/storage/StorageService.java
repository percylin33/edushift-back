package com.edushift.modules.files.storage;

import java.io.InputStream;
import java.util.UUID;

/**
 * Provider-agnostic binary store contract (Sprint 7a / BE-7a.0, ADR-7A.1).
 *
 * <p>Exactly one implementation is active at runtime, selected by
 * {@code app.storage.provider} ({@link StorageProvider}). The selection
 * is performed by
 * {@link com.edushift.modules.files.config.StorageAutoConfiguration} via
 * Spring's {@code @ConditionalOnProperty}.
 *
 * <h3>Multi-tenant safety (ADR-7A.2 + multi-tenant-audit §7)</h3>
 * <ul>
 *   <li>Every method takes the {@code tenantId} explicitly; the
 *       provider MUST scope the {@code remoteKey} so a future cross-tenant
 *       probe cannot land on another tenant's bytes.</li>
 *   <li>{@link #presignedGetUrl} and {@link #delete} validate the
 *       (provider, remoteKey) pair against the supplied {@code tenantId}
 *       where the provider can do so (LOCAL_FS scopes by directory;
 *       Firebase scopes by object name prefix).</li>
 *   <li>Cross-tenant lookups are NOT a concern of this interface: the
 *       service layer resolves the {@code FileObject} row first, and only
 *       then hands the (provider, key) to the provider. A tenant-A bearer
 *       cannot pass a tenant-B key because the row lookup itself
 *       404s.</li>
 * </ul>
 */
public interface StorageService {

	/**
	 * The provider backing this implementation. Used at registration
	 * time to assert the active provider matches {@code app.storage.provider}.
	 */
	StorageProvider provider();

	/**
	 * Persist the bytes for the given {@code (tenantId, module, publicUuid)}
	 * tuple. Computes the SHA-256 of the bytes, stores them, and returns
	 * the handle.
	 *
	 * @throws com.edushift.shared.exception.ApiException 503
	 *         {@code STORAGE_UNAVAILABLE} when the provider SDK fails
	 *         after one retry; 500 {@code FILE_CORRUPTED} if the
	 *         post-write read-back checksum does not match.
	 */
	StoredObject put(StoragePutRequest request);

	/**
	 * Open the bytes for reading. The caller closes the stream.
	 *
	 * @throws com.edushift.shared.exception.ApiException 404
	 *         {@code FILE_NOT_FOUND} when the (provider, key) tuple does
	 *         not exist; 500 {@code FILE_CORRUPTED} on checksum mismatch.
	 */
	InputStream open(UUID tenantId, String remoteKey);

	/**
	 * Read the full payload into memory. Intended only for small files
	 * (the attendance QR renderer is the only call site in this sprint).
	 * Use {@link #open} for streaming downloads.
	 */
	byte[] readAllBytes(UUID tenantId, String remoteKey);

	/**
	 * Build a time-limited URL the client can hit without a bearer.
	 * Returns {@code null} when the active provider does not support
	 * external URLs (e.g. LOCAL_FS with
	 * {@code serve-from-controller=true}); the caller is then expected
	 * to issue the in-app download endpoint instead.
	 *
	 * @param ttlSeconds URL validity window; ignored by providers that
	 *                   always go through the controller
	 */
	String presignedGetUrl(UUID tenantId, String remoteKey, long ttlSeconds);

	/**
	 * Mint a time-limited URL the client can {@code PUT} the bytes to
	 * without a bearer (V50 signed-upload flow, docs/infra/firebase.md).
	 *
	 * <p>Returns {@code null} when the active provider does not support
	 * direct uploads (LOCAL_FS — caller should fall back to
	 * {@link #put}). The controller hides this branch.</p>
	 *
	 * @param ttlSeconds URL validity window; provider-specific minimum
	 *                   may apply (e.g. GCS requires {@code > 0})
	 */
	String presignedPutUrl(UUID tenantId, String remoteKey, String contentType, long ttlSeconds);

	/**
	 * Remove the bytes. Idempotent — a missing object is not an error.
	 */
	void delete(UUID tenantId, String remoteKey);
}
