package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Authenticated principal lacks permission; HTTP 403.
 */
public class ForbiddenException extends ApiException {

	public ForbiddenException(String message) {
		super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
	}

	public ForbiddenException(String code, String message) {
		super(HttpStatus.FORBIDDEN, code, message);
	}

}
