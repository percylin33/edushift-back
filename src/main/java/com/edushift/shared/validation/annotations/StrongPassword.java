package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.StrongPasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces a configurable password complexity policy.
 * <p>
 * Defaults: 8-72 chars (72 is the bcrypt input limit), at least one upper
 * case, lower case, digit and non-alphanumeric character.
 * <p>
 * Use with {@code @NotBlank} when the password field is required; this
 * constraint accepts {@code null} so it can be combined with group-aware
 * presence checks (e.g. optional on PATCH).
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

	String message() default "{edushift.validation.StrongPassword.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int minLength() default 8;

	int maxLength() default 72;

	boolean requireUppercase() default true;

	boolean requireLowercase() default true;

	boolean requireDigit() default true;

	boolean requireSpecial() default true;

}
