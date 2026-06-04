package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Resource conflict (e.g. duplicate key, optimistic locking); HTTP 409.
 */
public class ConflictException extends ApiException {

	public ConflictException(String code, String message) {
		super(HttpStatus.CONFLICT, code, message);
	}

	public ConflictException(String code, String message, Throwable cause) {
		super(HttpStatus.CONFLICT, code, message, cause);
	}

}
