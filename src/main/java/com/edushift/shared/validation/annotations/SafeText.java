package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.SafeTextValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects strings containing HTML angle-brackets ({@code <}, {@code >}),
 * NUL bytes and non-printable ASCII control characters (other than
 * {@code TAB}, {@code LF}, {@code CR}).
 * <p>
 * Defence-in-depth against accidental XSS / log-injection in fields that
 * should hold plain text. Does <em>not</em> replace output escaping.
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

	String message() default "{edushift.validation.SafeText.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
