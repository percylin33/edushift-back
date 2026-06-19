package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails or is missing. Maps to HTTP 401
 * with the given code (e.g. {@code UNAUTHORIZED},
 * {@code AUTH_TOKEN_EXPIRED}, {@code AUTH_TOKEN_INVALID}).
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

    public UnauthorizedException(String code, String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, code, message, cause);
    }
}
