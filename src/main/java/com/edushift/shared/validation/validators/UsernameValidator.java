package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.Username;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Enforces the {@link Username} contract: 3-30 chars, alphanumerics plus
 * {@code . _ -}, first character alphanumeric.
 */
public class UsernameValidator implements ConstraintValidator<Username, CharSequence> {

	private static final Pattern PATTERN =
			Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{2,29}$");

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		return PATTERN.matcher(value).matches();
	}

}
