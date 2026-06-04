package com.edushift.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope:
 * <pre>{@code
 * {
 *   "success": false,
 *   "message": "...",
 *   "errors": [ { "code": "...", "field": "...", "message": "..." } ],
 *   "timestamp": "..."
 * }
 * }</pre>
 * {@code path} and {@code traceId} are included when available (debugging aid).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
		boolean success,
		String message,
		List<ApiError> errors,
		Instant timestamp,
		String path,
		String traceId
) {

	public static ApiErrorResponse of(String message, List<ApiError> errors, String path, String traceId) {
		return new ApiErrorResponse(false, message, errors, Instant.now(), path, traceId);
	}

	public static ApiErrorResponse of(String message, ApiError error, String path, String traceId) {
		return of(message, List.of(error), path, traceId);
	}

}
