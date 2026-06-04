package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.ValidUuid;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.UUID;

/**
 * Validates that the string parses as a {@link UUID} via the standard
 * 8-4-4-4-12 hex format (case-insensitive). Cheap and allocation-free for
 * the common rejection path (length check first).
 */
public class ValidUuidValidator implements ConstraintValidator<ValidUuid, CharSequence> {

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		if (value.length() != 36) {
			return false;
		}
		try {
			UUID.fromString(value.toString());
			return true;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

}
