package com.edushift.shared.validation.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composite constraint for required, policy-compliant passwords.
 * <p>
 * Combines {@link NotBlank} and {@link StrongPassword} (default rules:
 * 8-72 chars, mixed case + digit + special). Sub-violations collapse into a
 * single {@code WEAK_PASSWORD} error.
 */
@Documented
@NotBlank
@StrongPassword
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default "{edushift.validation.ValidPassword.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
