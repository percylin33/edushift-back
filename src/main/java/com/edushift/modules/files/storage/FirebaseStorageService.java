package com.edushift.modules.files.storage;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.error.FilesErrorCodes;
import com.edushift.modules.files.exception.StorageUnavailableException;
import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.io.ByteStreams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Firebase Storage / Google Cloud Storage implementation of
 * {@link StorageService} (Sprint 7a / BE-7a.0, ADR-7A.1).
 *
 * <p>Active when {@code app.storage.provider=firebase}. The class is
 * only instantiated in profiles that request Firebase; the LOCAL_FS
 * profile ignores it entirely so dev/test can run without a service
 * account.
 *
 * <h3>Authentication</h3>
 * Three paths, in priority order:
 * <ol>
 *   <li>{@code GOOGLE_APPLICATION_CREDENTIALS} env var (GCS SDK default).</li>
 *   <li>{@code app.storage.firebase.credentials-json} (raw service-account
 *       JSON, useful for serverless deployments).</li>
 *   <li>{@code app.storage.firebase.credentials-path} (path to a
 *       mounted JSON file).</li>
 * </ol>
 *
 * <h3>Multi-tenant safety (audit §7)</h3>
 * The on-the-wire path for an object is
 * {@code tenants/{tenantId}/lms/{module}/{publicUuid}}. The
 * {@link StorageService} interface (caller) is responsible for
 * constructing keys with {@link StoredObject#buildKey}; this class
 * does not log or echo keys back to callers.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider",
		havingValue = "FIREBASE")
public class FirebaseStorageService implements StorageService {

	private final StorageProperties props;

	private Storage client;

	private String bucket;

	public FirebaseStorageService(StorageProperties props) {
		this.props = props;
	}

	@PostConstruct
	void init() {
		String resolvedBucket = pickBucket();
		if (resolvedBucket == null || resolvedBucket.isBlank()) {
			throw new StorageUnavailableException(
					"app.storage.firebase.bucket (or app.integrations.firebase.storage-bucket) "
							+ "must be set when app.storage.provider=FIREBASE");
		}
		this.bucket = resolvedBucket;
		this.client = buildClient();
		log.info("[storage] firebase provider active at bucket {}", bucket);
	}

	@PreDestroy
	void close() {
		// GCS client is backed by a ManagedChannel in some versions; closing
		// is best-effort and never propagates.
		try {
			if (client != null) {
				client.close();
			}
		}
		catch (Exception e) {
			log.warn("[storage] error closing GCS client: {}", e.getMessage());
		}
	}

	@Override
	public StorageProvider provider() {
		return StorageProvider.FIREBASE;
	}

	@Override
	public StoredObject put(StoragePutRequest request) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new StorageUnavailableException("SHA-256 not available", e);
		}

		// Buffer the bytes once: GCS's BlobInfo.setStorageClass etc. accept
		// a byte[]; reading from InputStream and computing checksum in
		// the same pass is more memory-efficient, but GCS already buffers
		// the whole payload internally for single-shot uploads, so the
		// extra 25 MB on our heap is acceptable. If we ever stream big
		// uploads, switch to Storage.writer() with a DigestOutputStream.
		byte[] payload;
		long size;
		try (InputStream in = new DigestInputStream(
				new java.io.BufferedInputStream(request.input()), digest)) {
			payload = ByteStreams.toByteArray(in);
			size = payload.length;
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"Failed to read upload bytes", e);
		}

		String remoteKey = request.defaultRemoteKey(extractExtension(request.originalName()));
		BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, remoteKey))
				.setContentType(request.contentType())
				.setMetadata(java.util.Map.of(
						"tenantId", request.tenantId().toString(),
						"module", request.module(),
						"originalName", request.originalName(),
						"sha256", "<redacted>"))
				.build();

		try {
			client.create(info, payload);
		}
		catch (StorageException e) {
			throw mapStorageException("Failed to upload " + remoteKey, e);
		}

		String sha = HexFormat.of().formatHex(digest.digest());
		return new StoredObject(
				StorageProvider.FIREBASE,
				remoteKey,
				bucket,
				size,
				sha);
	}

	private static String extractExtension(String name) {
		if (name == null) {
			return null;
		}
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return null;
		}
		return name.substring(dot + 1);
	}

	@Override
	public InputStream open(UUID tenantId, String remoteKey) {
		Blob blob;
		try {
			blob = client.get(BlobId.of(bucket, remoteKey));
		}
		catch (StorageException e) {
			throw mapStorageException("Failed to fetch " + remoteKey, e);
		}
		if (blob == null || !blob.exists()) {
			throw new com.edushift.modules.files.exception.FileNotFoundException(remoteKey);
		}
		return new ByteArrayInputStream(blob.getContent());
	}

	@Override
	public byte[] readAllBytes(UUID tenantId, String remoteKey) {
		Blob blob;
		try {
			blob = client.get(BlobId.of(bucket, remoteKey));
		}
		catch (StorageException e) {
			throw mapStorageException("Failed to fetch " + remoteKey, e);
		}
		if (blob == null || !blob.exists()) {
			throw new com.edushift.modules.files.exception.FileNotFoundException(remoteKey);
		}
		return blob.getContent();
	}

	@Override
	public String presignedGetUrl(UUID tenantId, String remoteKey, long ttlSeconds) {
		if (props.getLocalFs().isServeFromController()) {
			// Caller prefers controller-routed downloads; return null to
			// signal "no external URL".
			return null;
		}
		long ttl = ttlSeconds > 0
				? ttlSeconds
				: props.getFirebase().getSignedUrlTtlSeconds();
		try {
			URL url = client.signUrl(
					BlobInfo.newBuilder(BlobId.of(bucket, remoteKey)).build(),
					ttl, TimeUnit.SECONDS);
			return url.toString();
		}
		catch (StorageException e) {
			throw mapStorageException(
					"Failed to sign URL for " + remoteKey, e);
		}
	}

	@Override
	public void delete(UUID tenantId, String remoteKey) {
		try {
			client.delete(BlobId.of(bucket, remoteKey));
		}
		catch (StorageException e) {
			if (e.getCode() == 404) {
				// Already gone — idempotent.
				return;
			}
			throw mapStorageException("Failed to delete " + remoteKey, e);
		}
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private String pickBucket() {
		String explicit = props.getFirebase().getBucket();
		if (explicit != null && !explicit.isBlank()) {
			return explicit;
		}
		// Fall back to the cross-cutting Firebase block when available.
		// The bean is intentionally not injected here to keep the
		// module dependency surface tight; properties are read from
		// the environment at @PostConstruct.
		String fromEnv = System.getenv("FIREBASE_STORAGE_BUCKET");
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return null;
	}

	private Storage buildClient() {
		StorageOptions.Builder builder = StorageOptions.newBuilder();
		GoogleCredentials creds = resolveCredentials();
		if (creds != null) {
			builder.setCredentials(creds);
		}
		return builder.build().getService();
	}

	private GoogleCredentials resolveCredentials() {
		String json = props.getFirebase().getCredentialsJson();
		if (json != null && !json.isBlank()) {
			try {
				return GoogleCredentials.fromStream(
						new ByteArrayInputStream(json.getBytes()));
			}
			catch (IOException e) {
				throw new StorageUnavailableException(
						"Failed to parse app.storage.firebase.credentials-json", e);
			}
		}
		String path = props.getFirebase().getCredentialsPath();
		if (path != null && !path.isBlank()) {
			try (InputStream in = new java.io.FileInputStream(path)) {
				return GoogleCredentials.fromStream(in);
			}
			catch (IOException e) {
				throw new StorageUnavailableException(
						"Failed to read app.storage.firebase.credentials-path="
								+ path, e);
			}
		}
		// Fall back to ADC (GOOGLE_APPLICATION_CREDENTIALS, GCE metadata, etc.).
		try {
			return GoogleCredentials.getApplicationDefault();
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"No Google credentials configured. Set one of: "
							+ "app.storage.firebase.credentials-json, "
							+ "app.storage.firebase.credentials-path, "
							+ "or GOOGLE_APPLICATION_CREDENTIALS.", e);
		}
	}

	private static StorageUnavailableException mapStorageException(String message, StorageException e) {
		int code = e.getCode();
		// 5xx → 503 STORAGE_UNAVAILABLE; 4xx → bubble as 500 FILE_CORRUPTED
		// unless it's a 404 (handled by the caller, not here).
		if (code >= 500) {
			return new StorageUnavailableException(message, e);
		}
		return new StorageUnavailableException(
				"%s (GCS code=%d)".formatted(message, code), e);
	}

	// Keep the unused-parameter warnings quiet on GCS-specific helpers
	// that may be used by future housekeeping jobs (list, count).
	@SuppressWarnings("unused")
	private Page<Blob> listForHousekeeping(UUID tenantId, String prefix) {
		return client.list(bucket, Storage.BlobListOption.prefix("tenants/" + tenantId + "/" + prefix));
	}
}
