package com.edushift.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for application-level exceptions handled by the global handler.
 * <p>
 * Subclasses bind an HTTP status and an error code consumed by REST clients.
 */
@Getter
public abstract class ApiException extends RuntimeException {

	private final String code;

	private final HttpStatus status;

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

}
