/**
 * Provider-agnostic storage abstraction (Sprint 7a / BE-7a.0, ADR-7A.1).
 * {@link com.edushift.modules.files.storage.StorageService} is the
 * contract; the two implementations
 * ({@link com.edushift.modules.files.storage.LocalFsStorageService}
 * and {@link com.edushift.modules.files.storage.FirebaseStorageService})
 * are selected by {@code app.storage.provider}.
 */
package com.edushift.modules.files.storage;
