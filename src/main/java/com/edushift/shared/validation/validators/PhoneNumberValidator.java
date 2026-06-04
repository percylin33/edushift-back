package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.PhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validates an international phone number loosely following E.164. Common
 * formatting characters (spaces, dashes, dots, parentheses) are stripped
 * prior to the regex check; remaining input must be 7-15 digits with an
 * optional leading {@code +}, first digit non-zero.
 */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, CharSequence> {

	private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

	private static final Pattern E164_LIKE = Pattern.compile("^\\+?[1-9]\\d{6,14}$");

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		String normalised = SEPARATORS.matcher(value).replaceAll("");
		if (normalised.isEmpty()) {
			return false;
		}
		return E164_LIKE.matcher(normalised).matches();
	}

}
