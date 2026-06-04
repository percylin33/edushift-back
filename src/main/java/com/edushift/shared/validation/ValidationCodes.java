package com.edushift.shared.validation;

/**
 * Canonical error codes emitted by validation failures in {@code ApiError.code}.
 * <p>
 * Stable contract for API consumers: codes never change even if messages do.
 * Treat the strings here as part of the public API.
 */
public final class ValidationCodes {

	public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

	// ---- Standard Jakarta constraints -----------------------------------------
	public static final String REQUIRED = "REQUIRED";

	public static final String SIZE_OUT_OF_RANGE = "SIZE_OUT_OF_RANGE";

	public static final String VALUE_TOO_SMALL = "VALUE_TOO_SMALL";

	public static final String VALUE_TOO_LARGE = "VALUE_TOO_LARGE";

	public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";

	public static final String INVALID_EMAIL = "INVALID_EMAIL";

	// ---- Custom EduShift constraints ------------------------------------------
	public static final String WEAK_PASSWORD = "WEAK_PASSWORD";

	public static final String INVALID_PHONE = "INVALID_PHONE";

	public static final String INVALID_TENANT_SLUG = "INVALID_TENANT_SLUG";

	public static final String INVALID_USERNAME = "INVALID_USERNAME";

	public static final String UNSAFE_TEXT = "UNSAFE_TEXT";

	public static final String INVALID_UUID = "INVALID_UUID";

	public static final String INVALID_ENUM_VALUE = "INVALID_ENUM_VALUE";

	private ValidationCodes() {
	}

}
