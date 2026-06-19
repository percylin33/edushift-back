package com.edushift.modules.files.storage;

/**
 * Identifies the backing binary store for a {@code lms_file_objects} row
 * (Sprint 7a / BE-7a.0).
 *
 * <p>The value is persisted in the {@code provider} column and gated by
 * {@code chk_file_objects_provider}. The string representation is the
 * contract; never rename without a migration.
 */
public enum StorageProvider {

	/** Google Cloud Storage (Firebase) bucket — default in prod/staging. */
	FIREBASE,

	/** Local filesystem under {@code app.storage.local-fs.root} — default in dev/test. */
	LOCAL_FS
}
