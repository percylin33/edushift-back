package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.TenantSlugValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a URL-safe tenant slug:
 * <ul>
 *   <li>3-50 characters</li>
 *   <li>only lowercase letters, digits and hyphens</li>
 *   <li>cannot start or end with a hyphen</li>
 *   <li>no consecutive hyphens</li>
 * </ul>
 * Examples: {@code "acme"}, {@code "school-42"}, {@code "edu-shift-pe"}.
 */
@Documented
@Constraint(validatedBy = TenantSlugValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantSlug {

	String message() default "{edushift.validation.TenantSlug.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
