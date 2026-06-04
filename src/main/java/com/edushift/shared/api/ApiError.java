package com.edushift.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Single error item inside {@link ApiErrorResponse#errors()}.
 * <ul>
 *   <li>{@code code}          - stable error code from {@code ValidationCodes}
 *                                or {@code ApiException.code} (part of the API contract)</li>
 *   <li>{@code field}         - populated for validation errors; null otherwise</li>
 *   <li>{@code message}       - human-readable, localized message</li>
 *   <li>{@code rejectedValue} - optional; included for validation errors when
 *                                safe to echo back (sensitive fields are masked)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String field, String message, Object rejectedValue) {

	public static ApiError of(String code, String message) {
		return new ApiError(code, null, message, null);
	}

	public static ApiError of(String code, String field, String message) {
		return new ApiError(code, field, message, null);
	}

	public static ApiError of(String code, String field, String message, Object rejectedValue) {
		return new ApiError(code, field, message, rejectedValue);
	}

}
