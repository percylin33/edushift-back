package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Resource is no longer available; HTTP 410.
 *
 * <p>Used for one-shot resources whose lifetime has terminated:
 * accepted-then-redeemed invitations, expired magic links, etc.
 * Distinct from 404 (the resource never existed) and 409
 * (the resource exists but a precondition fails).
 */
public class GoneException extends ApiException {

	public GoneException(String code, String message) {
		super(HttpStatus.GONE, code, message);
	}

	public GoneException(String code, String message, Throwable cause) {
		super(HttpStatus.GONE, code, message, cause);
	}

}
