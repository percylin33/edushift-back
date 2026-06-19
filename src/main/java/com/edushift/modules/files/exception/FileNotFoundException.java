package com.edushift.modules.files.exception;

import com.edushift.modules.files.error.FilesErrorCodes;
import com.edushift.shared.exception.NotFoundException;

/**
 * 404 with {@code FILE_NOT_FOUND} — the (provider, remoteKey) tuple
 * does not exist, or {@code remoteKey} escapes the tenant sandbox
 * (Sprint 7a / BE-7a.0).
 *
 * <p>Used by the storage layer; the controller layer also surfaces this
 * code when the {@code FileObject} row does not exist for the caller's
 * tenant (anti-enumeration: a 403 would leak the existence of
 * cross-tenant rows).
 */
public class FileNotFoundException extends NotFoundException {

	public FileNotFoundException(String remoteKey) {
		super(FilesErrorCodes.FILE_NOT_FOUND,
				"File not found: " + (remoteKey == null ? "<null>" : remoteKey));
	}
}
