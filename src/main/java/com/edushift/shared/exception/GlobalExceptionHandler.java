package com.edushift.shared.exception;

import com.edushift.shared.api.ApiError;
import com.edushift.shared.api.ApiErrorResponse;
import com.edushift.shared.constants.LoggerNames;
import com.edushift.shared.validation.ValidationCodes;
import com.edushift.shared.validation.ValidationErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized exception handler: maps exceptions to {@link ApiErrorResponse}
 * with the shape {@code {success, message, errors, timestamp}}.
 * <p>
 * Logging guidelines:
 * <ul>
 *   <li>4xx client errors → {@code WARN} with structured context (no stack trace)</li>
 *   <li>5xx server errors → {@code ERROR} with full stack trace</li>
 * </ul>
 * Structured fields emitted via SLF4J key-value args and MDC (path, method, status, traceId).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(LoggerNames.EXCEPTIONS);

	private static final String MDC_CORRELATION_ID = "correlationId";

	private static final String MDC_TRACE_ID = "traceId";

	// ---------------------------------------------------------------------------
	// Application exceptions
	// ---------------------------------------------------------------------------

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest req) {
		ApiError error = ApiError.of(ex.getCode(), ex.getMessage());
		return build(ex.getStatus(), ex.getMessage(), List.of(error), req, ex, false);
	}

	// ---------------------------------------------------------------------------
	// Validation (@Valid on @RequestBody)
	// ---------------------------------------------------------------------------

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException ex, HttpServletRequest req) {
		List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldError)
				.toList();
		return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, req, ex, false);
	}

	// ---------------------------------------------------------------------------
	// Validation on @PathVariable / @RequestParam (@Validated on controller)
	// ---------------------------------------------------------------------------

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException ex, HttpServletRequest req) {
		List<ApiError> errors = ex.getConstraintViolations().stream()
				.map(this::toConstraintError)
				.toList();
		return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, req, ex, false);
	}

	// ---------------------------------------------------------------------------
	// Malformed JSON / unreadable body
	// ---------------------------------------------------------------------------

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleNotReadable(
			HttpMessageNotReadableException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("MALFORMED_REQUEST", "Request body is malformed or missing");
		return build(HttpStatus.BAD_REQUEST, "Malformed request", List.of(error), req, ex, false);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParam(
			MissingServletRequestParameterException ex, HttpServletRequest req) {
		ApiError error = ApiError.of(ValidationCodes.REQUIRED, ex.getParameterName(),
				"Required parameter '%s' is missing".formatted(ex.getParameterName()));
		return build(HttpStatus.BAD_REQUEST, "Missing parameter", List.of(error), req, ex, false);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingHeader(
			MissingRequestHeaderException ex, HttpServletRequest req) {
		ApiError error = ApiError.of(ValidationCodes.REQUIRED, ex.getHeaderName(),
				"Required header '%s' is missing".formatted(ex.getHeaderName()));
		return build(HttpStatus.BAD_REQUEST, "Missing header", List.of(error), req, ex, false);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected";
		ApiError error = ApiError.of("TYPE_MISMATCH", ex.getName(),
				"Parameter '%s' must be of type %s".formatted(ex.getName(), expected),
				safeRejectedValue(ex.getName(), ex.getValue()));
		return build(HttpStatus.BAD_REQUEST, "Type mismatch", List.of(error), req, ex, false);
	}

	// ---------------------------------------------------------------------------
	// HTTP protocol errors
	// ---------------------------------------------------------------------------

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("METHOD_NOT_ALLOWED", ex.getMessage());
		return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", List.of(error), req, ex, false);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
			HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("UNSUPPORTED_MEDIA_TYPE", ex.getMessage());
		return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", List.of(error), req, ex, false);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResource(
			NoResourceFoundException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("ENDPOINT_NOT_FOUND", "No endpoint at %s".formatted(req.getRequestURI()));
		return build(HttpStatus.NOT_FOUND, "Not found", List.of(error), req, ex, false);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(
			MaxUploadSizeExceededException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("PAYLOAD_TOO_LARGE", "Uploaded file exceeds maximum allowed size");
		return build(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", List.of(error), req, ex, false);
	}

	// ---------------------------------------------------------------------------
	// Security
	// ---------------------------------------------------------------------------

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthentication(
			AuthenticationException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("UNAUTHORIZED", "Authentication required");
		return build(HttpStatus.UNAUTHORIZED, "Unauthorized", List.of(error), req, ex, false);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleAccessDenied(
			AccessDeniedException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("FORBIDDEN", "Access is denied");
		return build(HttpStatus.FORBIDDEN, "Forbidden", List.of(error), req, ex, false);
	}

	// ---------------------------------------------------------------------------
	// Persistence
	// ---------------------------------------------------------------------------

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
			DataIntegrityViolationException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("DATA_INTEGRITY_VIOLATION",
				"Operation violates a data integrity constraint");
		return build(HttpStatus.CONFLICT, "Conflict", List.of(error), req, ex, true);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(
			OptimisticLockingFailureException ex, HttpServletRequest req) {
		ApiError error = ApiError.of("OPTIMISTIC_LOCK",
				"Resource was modified concurrently; please retry");
		return build(HttpStatus.CONFLICT, "Conflict", List.of(error), req, ex, true);
	}

	// ---------------------------------------------------------------------------
	// Fallback
	// ---------------------------------------------------------------------------

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
		ApiError error = ApiError.of("INTERNAL_ERROR", "An unexpected error occurred");
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", List.of(error), req, ex, true);
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	private ResponseEntity<ApiErrorResponse> build(
			HttpStatus status,
			String message,
			List<ApiError> errors,
			HttpServletRequest req,
			Exception ex,
			boolean serverError) {

		String path = req.getRequestURI();
		String traceId = currentTraceId();
		logException(status, message, path, req.getMethod(), ex, serverError);
		ApiErrorResponse body = ApiErrorResponse.of(message, errors, path, traceId);
		return ResponseEntity.status(status).body(body);
	}

	private void logException(
			HttpStatus status,
			String message,
			String path,
			String method,
			Exception ex,
			boolean serverError) {

		if (serverError || status.is5xxServerError()) {
			log.error("api_error status={} method={} path={} message=\"{}\" exception={}",
					status.value(), method, path, message, ex.getClass().getSimpleName(), ex);
		}
		else {
			log.warn("api_warn status={} method={} path={} message=\"{}\" exception={} detail=\"{}\"",
					status.value(), method, path, message, ex.getClass().getSimpleName(), ex.getMessage());
		}
	}

	private String currentTraceId() {
		String correlationId = MDC.get(MDC_CORRELATION_ID);
		if (correlationId != null && !correlationId.isBlank()) {
			return correlationId;
		}
		String traceId = MDC.get(MDC_TRACE_ID);
		if (traceId != null && !traceId.isBlank()) {
			return traceId;
		}
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private ApiError toFieldError(FieldError fe) {
		String message = fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value";
		String code = ValidationErrorCodes.resolve(fe.getCode());
		return ApiError.of(code, fe.getField(), message, safeRejectedValue(fe.getField(), fe.getRejectedValue()));
	}

	private ApiError toConstraintError(ConstraintViolation<?> cv) {
		String field = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : null;
		String annotation = cv.getConstraintDescriptor() != null
				? cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
				: null;
		String code = ValidationErrorCodes.resolve(annotation);
		return ApiError.of(code, field, cv.getMessage(), safeRejectedValue(field, cv.getInvalidValue()));
	}

	/**
	 * Returns the rejected value only when echoing it back is safe (no
	 * credentials / tokens), trimming arbitrarily large strings.
	 */
	private Object safeRejectedValue(String field, Object value) {
		if (value == null || isSensitiveField(field)) {
			return null;
		}
		if (value instanceof CharSequence cs && cs.length() > 200) {
			return cs.subSequence(0, 200).toString() + "…";
		}
		return value;
	}

	private boolean isSensitiveField(String field) {
		if (field == null) {
			return false;
		}
		String lower = field.toLowerCase();
		return lower.contains("password")
				|| lower.contains("secret")
				|| lower.contains("token")
				|| lower.contains("apikey")
				|| lower.contains("api_key")
				|| lower.contains("credential")
				|| lower.endsWith("pwd");
	}

}
