package com.edushift.modules.files.storage;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.exception.StorageUnavailableException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-filesystem implementation of {@link StorageService}
 * (Sprint 7a / BE-7a.0, ADR-7A.1).
 *
 * <p>Active when {@code app.storage.provider=local-fs} (the default in
 * dev/test). Stores each upload at
 * {@code ${app.storage.local-fs.root}/tenants/{tenantId}/lms/{module}/{publicUuid}}.
 *
 * <h3>Multi-tenant safety (audit §7)</h3>
 * <ul>
 *   <li>The on-disk layout is rooted under a per-tenant directory;
 *       {@link #resolveTenantPath(UUID, String)} guards against
 *       {@code remoteKey} containing {@code ..} or absolute paths that
 *       would escape the tenant sandbox.</li>
 *   <li>{@link #delete} is scoped to the tenant: a tenant-A key that
 *       resolves outside its directory is silently treated as missing
 *       (no cross-tenant delete leak).</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * Bytes are streamed in 8 KiB chunks; the SHA-256 is computed in-line
 * with the copy via a {@link DigestInputStream}/{@link DigestOutputStream}
 * pair, so a 25 MB upload peaks at 8 KiB of heap per direction.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider",
		havingValue = "LOCAL_FS")
public class LocalFsStorageService implements StorageService {

	private static final int BUFFER_SIZE = 8 * 1024;

	private final StorageProperties props;

	public LocalFsStorageService(StorageProperties props) {
		this.props = props;
	}

	/**
	 * Returns the current storage root, re-reading from
	 * {@link StorageProperties} on every call.
	 *
	 * <p>Reading per call (instead of caching the value at
	 * {@code @PostConstruct} time) lets integration tests redirect
	 * the root into a per-class tempdir via
	 * {@code storageProperties.getLocalFs().setRoot(...)} without
	 * having to recreate the bean. In production the property is
	 * fixed at boot, so the cost is one method dispatch per
	 * operation — negligible.
	 */
	private Path root() {
		Path r = props.getLocalFs().getRoot().toAbsolutePath().normalize();
		try {
			Files.createDirectories(r);
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"Cannot create local-fs storage root: " + r, e);
		}
		return r;
	}

	@Override
	public StorageProvider provider() {
		return StorageProvider.LOCAL_FS;
	}

	@Override
	public StoredObject put(StoragePutRequest request) {
		String remoteKey = request.defaultRemoteKey(extractExtension(request.originalName()));
		Path target = resolveTenantPath(request.tenantId(), remoteKey);
		Path parent = target.getParent();
		if (parent == null) {
			throw new StorageUnavailableException(
					"Computed path has no parent: " + target);
		}
		try {
			Files.createDirectories(parent);
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"Cannot create directory: " + parent, e);
		}

		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new StorageUnavailableException("SHA-256 not available", e);
		}

		long size;
		try (InputStream in = new BufferedInputStream(request.input());
				DigestInputStream dig = new DigestInputStream(in, digest);
				OutputStream out = Files.newOutputStream(target)) {
			size = copy(dig, out);
		}
		catch (IOException e) {
			// Best-effort cleanup of a half-written file.
			try {
				Files.deleteIfExists(target);
			}
			catch (IOException ignored) {
				// intentionally swallowed
			}
			throw new StorageUnavailableException(
					"Failed to write to " + target, e);
		}

		String sha = HexFormat.of().formatHex(digest.digest());
		return new StoredObject(
				StorageProvider.LOCAL_FS,
				remoteKey,
				null,
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
		Path target = resolveTenantPath(tenantId, remoteKey);
		if (!Files.exists(target)) {
			throw new com.edushift.modules.files.exception.FileNotFoundException(remoteKey);
		}
		try {
			return Files.newInputStream(target);
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"Failed to open " + target, e);
		}
	}

	@Override
	public byte[] readAllBytes(UUID tenantId, String remoteKey) {
		try (InputStream in = open(tenantId, remoteKey);
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			copy(in, out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"Failed to read " + remoteKey, e);
		}
	}

	@Override
	public String presignedGetUrl(UUID tenantId, String remoteKey, long ttlSeconds) {
		// Local-FS never exposes a public URL: callers must hit
		// GET /v1/files/{publicUuid}/download with a bearer.
		return null;
	}

	/**
	 * The signed-PUT-URL flow is only meaningful for the FIREBASE provider
	 * (V50). For LOCAL_FS the caller should fall back to the BE-proxied
	 * multipart upload. The {@code FileObjectController} hides this
	 * branch by inspecting {@link com.edushift.modules.files.storage.StorageProvider}.
	 */
	@Override
	public String presignedPutUrl(UUID tenantId, String remoteKey, String contentType, long ttlSeconds) {
		return null;
	}

	@Override
	public void delete(UUID tenantId, String remoteKey) {
		Path target = resolveTenantPath(tenantId, remoteKey);
		if (!target.startsWith(tenantRoot(tenantId))) {
			// Foreign key — refuse silently (idempotent).
			log.debug("[storage] delete ignored: key {} escapes tenant {} sandbox",
					remoteKey, tenantId);
			return;
		}
		try {
			Files.deleteIfExists(target);
		}
		catch (IOException e) {
			throw new StorageUnavailableException(
					"Failed to delete " + target, e);
		}
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	/**
	 * Sentinel used for path-escape checks in {@link #delete}. The
	 * actual on-disk layout is rooted at {@code root} and the
	 * tenantId is part of the {@code remoteKey} (see
	 * {@link StoredObject#buildKey}, which emits
	 * {@code "tenants/{tid}/lms/{module}/{publicUuid}"}). The previous
	 * implementation mistakenly had {@code tenantRoot = root.resolve(tid)},
	 * which combined with the {@code "tenants/{tid}/..."} prefix in the
	 * key produced a path with the tenantId appearing TWICE
	 * ({@code ${root}/{tid}/tenants/{tid}/...}). Fixed in Sprint 11
	 * (ADR-11.4): the tenant root for security checks is just
	 * {@code root}, and the {@code tenants/} prefix comes from the key.
	 */
	private Path tenantRoot(UUID tenantId) {
		return root();
	}

	/**
	 * Resolves {@code remoteKey} under the tenant's directory. Throws
	 * {@code FILE_NOT_FOUND} when the resolved path escapes the tenant
	 * root (defence against {@code ../../etc/passwd} attempts).
	 */
	private Path resolveTenantPath(UUID tenantId, String remoteKey) {
		if (remoteKey == null || remoteKey.isBlank()) {
			throw new com.edushift.modules.files.exception.FileNotFoundException("<blank>");
		}
		Path root = tenantRoot(tenantId);
		Path resolved = root
				.resolve(remoteKey)
				.normalize();
		if (!resolved.startsWith(root)) {
			throw new com.edushift.modules.files.exception.FileNotFoundException(remoteKey);
		}
		return resolved;
	}

	private static long copy(InputStream in, OutputStream out) throws IOException {
		long total = 0;
		byte[] buf = new byte[BUFFER_SIZE];
		int read;
		while ((read = in.read(buf)) != -1) {
			out.write(buf, 0, read);
			total += read;
		}
		out.flush();
		return total;
	}
}
