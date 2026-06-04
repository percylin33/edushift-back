package com.edushift.shared.validation;

import java.util.Map;

/**
 * Resolves an {@link jakarta.validation.ConstraintViolation} (or Spring
 * {@link org.springframework.validation.FieldError}) to a stable
 * {@link ValidationCodes} string.
 * <p>
 * Resolution is by the annotation's <em>simple name</em>
 * (e.g. {@code "NotNull"}, {@code "StrongPassword"}) which is what Spring's
 * {@code FieldError.getCode()} returns and what Hibernate Validator exposes
 * through {@code ConstraintDescriptor.getAnnotation().annotationType()}.
 * <p>
 * Unknown annotations fall back to {@link ValidationCodes#VALIDATION_ERROR}.
 */
public final class ValidationErrorCodes {

	private static final Map<String, String> BY_ANNOTATION = Map.ofEntries(
			// Jakarta Bean Validation - presence
			Map.entry("NotNull", ValidationCodes.REQUIRED),
			Map.entry("NotBlank", ValidationCodes.REQUIRED),
			Map.entry("NotEmpty", ValidationCodes.REQUIRED),
			Map.entry("Null", "MUST_BE_NULL"),
			// Format
			Map.entry("Email", ValidationCodes.INVALID_EMAIL),
			Map.entry("Pattern", ValidationCodes.PATTERN_MISMATCH),
			Map.entry("Digits", "INVALID_NUMBER_FORMAT"),
			// Size / range
			Map.entry("Size", ValidationCodes.SIZE_OUT_OF_RANGE),
			Map.entry("Min", ValidationCodes.VALUE_TOO_SMALL),
			Map.entry("Max", ValidationCodes.VALUE_TOO_LARGE),
			Map.entry("DecimalMin", ValidationCodes.VALUE_TOO_SMALL),
			Map.entry("DecimalMax", ValidationCodes.VALUE_TOO_LARGE),
			Map.entry("Positive", "MUST_BE_POSITIVE"),
			Map.entry("PositiveOrZero", "MUST_BE_NON_NEGATIVE"),
			Map.entry("Negative", "MUST_BE_NEGATIVE"),
			Map.entry("NegativeOrZero", "MUST_BE_NON_POSITIVE"),
			// Boolean
			Map.entry("AssertTrue", "MUST_BE_TRUE"),
			Map.entry("AssertFalse", "MUST_BE_FALSE"),
			// Temporal
			Map.entry("Past", "MUST_BE_PAST"),
			Map.entry("PastOrPresent", "MUST_BE_PAST_OR_PRESENT"),
			Map.entry("Future", "MUST_BE_FUTURE"),
			Map.entry("FutureOrPresent", "MUST_BE_FUTURE_OR_PRESENT"),
			// Custom EduShift constraints
			Map.entry("StrongPassword", ValidationCodes.WEAK_PASSWORD),
			Map.entry("ValidPassword", ValidationCodes.WEAK_PASSWORD),
			Map.entry("PhoneNumber", ValidationCodes.INVALID_PHONE),
			Map.entry("TenantSlug", ValidationCodes.INVALID_TENANT_SLUG),
			Map.entry("Username", ValidationCodes.INVALID_USERNAME),
			Map.entry("SafeText", ValidationCodes.UNSAFE_TEXT),
			Map.entry("ValidUuid", ValidationCodes.INVALID_UUID),
			Map.entry("EnumValue", ValidationCodes.INVALID_ENUM_VALUE),
			Map.entry("ValidEmail", ValidationCodes.INVALID_EMAIL)
	);

	private ValidationErrorCodes() {
	}

	public static String resolve(String annotationSimpleName) {
		if (annotationSimpleName == null || annotationSimpleName.isBlank()) {
			return ValidationCodes.VALIDATION_ERROR;
		}
		return BY_ANNOTATION.getOrDefault(annotationSimpleName, ValidationCodes.VALIDATION_ERROR);
	}

}
