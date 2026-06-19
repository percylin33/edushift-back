package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Resource not found; HTTP 404.
 */
public class NotFoundException extends ApiException {
    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
