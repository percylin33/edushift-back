package com.edushift.modules.files.config;

import com.edushift.modules.files.storage.StorageProvider;
import java.nio.file.Path;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage provider configuration (Sprint 7a / BE-7a.0, ADR-7A.1).
 *
 * <p>Bound to {@code app.storage.*}. Exactly one provider is active per
 * profile — see {@link com.edushift.modules.files.config.StorageAutoConfiguration}
 * for the selector logic.
 *
 * <h3>Property map</h3>
 * <ul>
 *   <li>{@code app.storage.provider} — {@code FIREBASE} or {@code LOCAL_FS} (mandatory at startup; fail-fast otherwise).</li>
 *   <li>{@code app.storage.max-file-size-bytes} — per-file cap. Default 25 MB. Spring multipart is also configured from this value.</li>
 *   <li>{@code app.storage.allowed-content-types} — MIME allow-list. Default: PDF, images, MS Office, OpenDocument, plain text, ZIP.</li>
 *   <li>{@code app.storage.local-fs.root} — absolute path where Local-FS provider stores blobs. Default: {@code ./edushift-storage}.</li>
 *   <li>{@code app.storage.local-fs.serve-from-controller} — when true, files are served through {@code GET /v1/files/{uuid}/download} with auth; when false, the storage provider returns a public URL (only safe for Firebase with signed URLs). Default: true.</li>
 *   <li>{@code app.storage.firebase.bucket} — GCS bucket name. Re-uses {@code app.integrations.firebase.storage-bucket} if empty.</li>
 *   <li>{@code app.storage.firebase.credentials-json} / {@code ...credentials-path} — same convention as the Firebase block in {@code application.properties}.</li>
 *   <li>{@code app.storage.firebase.signed-url-ttl-seconds} — TTL for the download URL when {@code serve-from-controller=false}. Default 900 (15 min).</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

	/**
	 * Active provider. Validated at startup by
	 * {@link com.edushift.modules.files.config.StorageAutoConfiguration};
	 * an unknown value makes the application refuse to boot.
	 */
	private StorageProvider provider = StorageProvider.LOCAL_FS;

	/**
	 * Maximum per-file size in bytes. Default 25 MiB.
	 * Mirrored to {@code spring.servlet.multipart.max-file-size} at
	 * startup so the framework enforces it before the controller sees
	 * the request.
	 */
	private long maxFileSizeBytes = 25L * 1024L * 1024L;

	/**
	 * MIME allow-list. Lower-cased for case-insensitive matching.
	 * Defaults to a safe educational set; tenant admins cannot widen it
	 * in this sprint (DEBT-7A-1 is the storage quota track; widening
	 * the allow-list is a separate concern).
	 */
	private List<String> allowedContentTypes = List.of(
			"application/pdf",
			"image/png",
			"image/jpeg",
			"image/webp",
			"image/gif",
			"image/svg+xml",
			"application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/vnd.ms-excel",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"application/vnd.ms-powerpoint",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"text/plain",
			"text/csv",
			"application/zip"
	);

	private final LocalFs localFs = new LocalFs();

	private final Firebase firebase = new Firebase();

	@Getter
	@Setter
	public static class LocalFs {

		/**
		 * Filesystem root for the Local-FS provider. Created at startup
		 * if missing.
		 */
		private Path root = Path.of("./edushift-storage");

		/**
		 * When true, downloads go through {@code GET /v1/files/{uuid}/download}
		 * with bearer auth (recommended for dev/test). When false, the
		 * provider returns an external URL (only valid for Firebase signed
		 * URLs in this sprint).
		 */
		private boolean serveFromController = true;
	}

	@Getter
	@Setter
	public static class Firebase {

		/**
		 * GCS bucket name. When empty, falls back to
		 * {@code app.integrations.firebase.storage-bucket}.
		 */
		private String bucket;

		/**
		 * Raw service-account JSON. Mutually exclusive with
		 * {@link #credentialsPath}.
		 */
		private String credentialsJson;

		/**
		 * Path to a service-account JSON file. Mutually exclusive with
		 * {@link #credentialsJson}.
		 */
		private String credentialsPath;

		/**
		 * TTL of the signed download URL (only used when
		 * {@link LocalFs#serveFromController} is false). Default 900s.
		 */
		private long signedUrlTtlSeconds = 900L;
	}
}
