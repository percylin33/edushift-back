package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Authentication required or invalid credentials; HTTP 401.
 */
public class UnauthorizedException extends ApiException {

	public UnauthorizedException(String message) {
		super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
	}

	public UnauthorizedException(String code, String message) {
		super(HttpStatus.UNAUTHORIZED, code, message);
	}

}
