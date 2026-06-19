package com.edushift.modules.files.exception;

import com.edushift.modules.files.error.FilesErrorCodes;
import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 413 — uploaded file exceeds the configured maximum size
 * (Sprint 7a / BE-7a.0). Spring's
 * {@code MaxUploadSizeExceededException} is mapped to the same code by
 * the global exception handler; this class is the defence-in-depth
 * entry point for callers that bypass Spring multipart (e.g. raw
 * {@code InputStream} put through the storage service).
 */
public class FileTooLargeException extends ApiException {

	public FileTooLargeException(long declaredSizeBytes, long maxBytes) {
		super(HttpStatus.PAYLOAD_TOO_LARGE,
				FilesErrorCodes.FILE_TOO_LARGE,
				"File size %d bytes exceeds the %d-byte limit"
						.formatted(declaredSizeBytes, maxBytes));
	}
}
