package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.UsernameValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a human-friendly username:
 * <ul>
 *   <li>3-30 characters</li>
 *   <li>letters, digits, dot, underscore or hyphen</li>
 *   <li>first character must be a letter or digit</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = UsernameValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Username {

	String message() default "{edushift.validation.Username.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
