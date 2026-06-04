package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.StrongPassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates the policy declared by {@link StrongPassword}.
 * Null values are skipped (combine with {@code @NotBlank} when required).
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, CharSequence> {

	private int minLength;

	private int maxLength;

	private boolean requireUppercase;

	private boolean requireLowercase;

	private boolean requireDigit;

	private boolean requireSpecial;

	@Override
	public void initialize(StrongPassword ann) {
		this.minLength = ann.minLength();
		this.maxLength = ann.maxLength();
		this.requireUppercase = ann.requireUppercase();
		this.requireLowercase = ann.requireLowercase();
		this.requireDigit = ann.requireDigit();
		this.requireSpecial = ann.requireSpecial();
	}

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		int length = value.length();
		if (length < minLength || length > maxLength) {
			return false;
		}

		boolean hasUpper = false;
		boolean hasLower = false;
		boolean hasDigit = false;
		boolean hasSpecial = false;

		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);
			if (Character.isUpperCase(c)) {
				hasUpper = true;
			}
			else if (Character.isLowerCase(c)) {
				hasLower = true;
			}
			else if (Character.isDigit(c)) {
				hasDigit = true;
			}
			else if (!Character.isWhitespace(c)) {
				hasSpecial = true;
			}
		}

		if (requireUppercase && !hasUpper) {
			return false;
		}
		if (requireLowercase && !hasLower) {
			return false;
		}
		if (requireDigit && !hasDigit) {
			return false;
		}
		if (requireSpecial && !hasSpecial) {
			return false;
		}
		return true;
	}

}
