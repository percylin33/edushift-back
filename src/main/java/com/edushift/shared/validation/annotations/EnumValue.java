package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.EnumValueValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@code String} value matches one of the declared
 * {@link Enum} constants.
 * <p>
 * Useful when a DTO carries an enum as a string from the JSON payload and
 * you want to surface a clean validation error instead of a deserialization
 * failure ({@code HttpMessageNotReadableException}).
 *
 * <pre>{@code
 * @EnumValue(enumClass = Status.class)
 * private String status;
 * }</pre>
 */
@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {

	String message() default "{edushift.validation.EnumValue.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	Class<? extends Enum<?>> enumClass();

	boolean ignoreCase() default false;

}
