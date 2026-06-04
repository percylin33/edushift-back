package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.PhoneNumberValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates an international phone number, loosely following E.164:
 * optional leading {@code +}, then 7-15 digits, first digit non-zero.
 * <p>
 * Common separators ({@code -}, space, parentheses, dot) are stripped before
 * validation, so {@code "+1 (555) 123-4567"} is accepted.
 * Accepts {@code null}; combine with {@code @NotBlank} for required fields.
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

	String message() default "{edushift.validation.PhoneNumber.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
