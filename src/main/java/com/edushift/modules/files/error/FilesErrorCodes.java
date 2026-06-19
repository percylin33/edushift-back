package com.edushift.modules.files.error;

/**
 * Stable error codes for the {@code files} module (Sprint 7a / BE-7a.0).
 *
 * <p>Codes are part of the public API contract — never rename. The full
 * error contract (HTTP status, recovery hints) is documented in
 * {@code docs/modules/files.md}.
 *
 * <h3>Grouping</h3>
 * <ul>
 *   <li>{@code FILE_TOO_LARGE} — Spring {@code MaxUploadSizeExceededException} mapped to 413.</li>
 *   <li>{@code FILE_TYPE_NOT_ALLOWED} — extension not in the whitelist (415).</li>
 *   <li>{@code STORAGE_UNAVAILABLE} — provider SDK threw (Firebase 5xx, disk full, etc.) (503).</li>
 *   <li>{@code FILE_NOT_FOUND} — public UUID does not match a non-deleted row, or cross-tenant access (404).</li>
 *   <li>{@code FILE_CORRUPTED} — checksum mismatch on read (500).</li>
 *   <li>{@code UNKNOWN_STORAGE_PROVIDER} — misconfigured {@code app.storage.provider} (fail-fast at startup).</li>
 *   <li>{@code STORAGE_NOT_CONFIGURED} — provider is enabled but credentials are missing (fail-fast at startup).</li>
 * </ul>
 */
public final class FilesErrorCodes {

	/** 413 — uploaded file exceeds the configured maximum size. */
	public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

	/** 415 — uploaded file's content type is not in the allow-list. */
	public static final String FILE_TYPE_NOT_ALLOWED = "FILE_TYPE_NOT_ALLOWED";

	/** 503 — provider SDK unreachable or returned a 5xx. */
	public static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";

	/** 404 — public UUID unknown, or belongs to another tenant (anti-enumeration). */
	public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";

	/** 500 — SHA-256 mismatch between DB metadata and the bytes the provider returned. */
	public static final String FILE_CORRUPTED = "FILE_CORRUPTED";

	/** 500 — {@code app.storage.provider} is set to a value not in {@link com.edushift.modules.files.storage.StorageProvider}. */
	public static final String UNKNOWN_STORAGE_PROVIDER = "UNKNOWN_STORAGE_PROVIDER";

	/** 500 — provider is configured but credentials / required properties are missing. */
	public static final String STORAGE_NOT_CONFIGURED = "STORAGE_NOT_CONFIGURED";

	private FilesErrorCodes() {
	}
}
