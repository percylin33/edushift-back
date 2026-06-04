package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain rule violation; HTTP 422.
 */
public class BusinessException extends ApiException {

	public BusinessException(String code, String message) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}

	public BusinessException(String code, String message, Throwable cause) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, code, message, cause);
	}

}
