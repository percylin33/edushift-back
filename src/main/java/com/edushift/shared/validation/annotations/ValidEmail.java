package com.edushift.shared.validation.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composite constraint for required, well-formed email addresses.
 * <p>
 * Combines {@link NotBlank}, {@link Email} and {@link Size}(max=254).
 * {@link ReportAsSingleViolation} collapses the sub-violations into a single
 * {@code INVALID_EMAIL} error.
 */
@Documented
@NotBlank
@Email
@Size(max = 254)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

	String message() default "{edushift.validation.ValidEmail.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
