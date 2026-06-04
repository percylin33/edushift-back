package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.SafeText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link SafeText}: rejects {@code <}, {@code >}, NUL and ASCII
 * control characters other than tab, line feed and carriage return.
 */
public class SafeTextValidator implements ConstraintValidator<SafeText, CharSequence> {

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		int length = value.length();
		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);
			if (c == '<' || c == '>' || c == '\0') {
				return false;
			}
			if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
				return false;
			}
			if (c == 0x7F) {
				return false;
			}
		}
		return true;
	}

}
