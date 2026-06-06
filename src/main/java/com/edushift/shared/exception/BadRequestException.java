package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Client-side semantic error; HTTP 400.
 *
 * <p>Used when the request shape is well-formed (so it survives Bean
 * Validation) but a cross-field or domain-level pre-condition has been
 * violated and we want to short-circuit before the service performs
 * any expensive work. Examples:
 * <ul>
 *   <li>{@code INVALID_WITHDRAW_STATUS} — caller asked to withdraw a
 *       student enrollment but supplied a non-terminal status
 *       ({@code ACTIVE}).</li>
 *   <li>{@code VALIDATION_ERROR} — cross-field date constraint such as
 *       {@code withdrawnAt < enrolledAt}.</li>
 * </ul>
 *
 * <p>Domain rule violations that surface as 422 ("the data is fine but
 * the business says no") should keep using {@link BusinessException};
 * uniqueness / state conflicts that surface as 409 should keep using
 * {@link ConflictException}.
 */
public class BadRequestException extends ApiException {

	public BadRequestException(String code, String message) {
		super(HttpStatus.BAD_REQUEST, code, message);
	}

	public BadRequestException(String code, String message, Throwable cause) {
		super(HttpStatus.BAD_REQUEST, code, message, cause);
	}

}
