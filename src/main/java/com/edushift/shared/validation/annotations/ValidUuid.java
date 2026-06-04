package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.ValidUuidValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@code String} is a syntactically valid UUID
 * (8-4-4-4-12 hex format, case-insensitive).
 * <p>
 * Prefer typing IDs as {@link java.util.UUID} when possible; reach for this
 * constraint when the API receives UUIDs as plain strings (e.g. path
 * variables coming from a generic search endpoint).
 */
@Documented
@Constraint(validatedBy = ValidUuidValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUuid {

	String message() default "{edushift.validation.ValidUuid.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
