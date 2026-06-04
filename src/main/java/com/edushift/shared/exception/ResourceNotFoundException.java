package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Resource not found; HTTP 404.
 */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException(String resource, Object id) {
		super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
				"%s not found with id: %s".formatted(resource, id));
	}

	public ResourceNotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
	}

}
