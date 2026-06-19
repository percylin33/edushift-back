package com.edushift.modules.files.exception;

import com.edushift.modules.files.error.FilesErrorCodes;
import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 415 — uploaded file's content type is not in the configured allow-list
 * (Sprint 7a / BE-7a.0).
 */
public class FileTypeNotAllowedException extends ApiException {

	public FileTypeNotAllowedException(String contentType) {
		super(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				FilesErrorCodes.FILE_TYPE_NOT_ALLOWED,
				"File content type is not allowed: " + contentType);
	}
}
