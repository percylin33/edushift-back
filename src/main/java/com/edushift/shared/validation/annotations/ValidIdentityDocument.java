package com.edushift.shared.validation.annotations;

import com.edushift.shared.validation.validators.IdentityDocumentValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates {@code documentNumber} shape according to {@code documentType}. */
@Documented
@Constraint(validatedBy = IdentityDocumentValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIdentityDocument {

	String message() default "{edushift.validation.ValidIdentityDocument.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
