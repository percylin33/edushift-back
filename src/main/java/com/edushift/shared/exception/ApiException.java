package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for all EduShift domain exceptions that map cleanly to HTTP
 * status codes. The {@code GlobalExceptionHandler} converts each
 * subclass to an {@code ApiResponse} with the matching status.
 *
 * <p>Use this base for any exception that should be surfaced to the
 * API consumer with a stable {@code code} (e.g. for FE i18n lookup)
 * and a human-friendly {@code message} (e.g. for the error toast).</p>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode()       { return code; }
}
