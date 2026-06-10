package com.edushift.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * 404 with a domain-specific error code (as opposed to
 * {@link ResourceNotFoundException} which always emits the generic
 * {@code RESOURCE_NOT_FOUND}).
 *
 * <p>Use this when "the URL is well-formed and the parent resource
 * exists, but a sub-resource is not currently set" — and the FE needs
 * to react differently than a generic "not found" (e.g. show a
 * "no rubric attached" empty state instead of a 404 page).</p>
 *
 * <p>Example: {@code EVAL_RUBRIC_NOT_SET} — the evaluation exists for
 * the tenant but has no rubric associated; GET / DELETE on
 * {@code /v1/evaluations/{uuid}/rubric} surface this code.</p>
 */
public class NotFoundException extends ApiException {

	public NotFoundException(String code, String message) {
		super(HttpStatus.NOT_FOUND, code, message);
	}

	public NotFoundException(String code, String message, Throwable cause) {
		super(HttpStatus.NOT_FOUND, code, message, cause);
	}
}
