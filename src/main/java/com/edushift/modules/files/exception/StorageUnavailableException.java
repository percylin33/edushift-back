package com.edushift.modules.files.exception;

import com.edushift.modules.files.error.FilesErrorCodes;
import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 503 — the storage provider SDK is unreachable, returns 5xx, or the
 * local disk is full (Sprint 7a / BE-7a.0).
 *
 * <p>Wraps a {@code STORAGE_UNAVAILABLE} error in the standard
 * {@code ApiErrorResponse} envelope.
 */
public class StorageUnavailableException extends ApiException {

	public StorageUnavailableException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, FilesErrorCodes.STORAGE_UNAVAILABLE, message);
	}

	public StorageUnavailableException(String message, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, FilesErrorCodes.STORAGE_UNAVAILABLE, message, cause);
	}
}
