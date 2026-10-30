package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.StrongPassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

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

	private static final Set<String> COMMON_PASSWORDS = Set.of(
		"password", "password1", "password123", "123456", "12345678",
		"qwerty", "qwerty123", "abc123", "letmein", "welcome1",
		"admin123", "root123", "pass123", "user123", "test123"
	);

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		int length = value.length();
		if (length < minLength || length > maxLength) {
			return false;
		}

		// Rechazar contraseñas comunes
		String lower = value.toString().toLowerCase();
		if (COMMON_PASSWORDS.contains(lower)) {
			return false;
		}

		// Rechazar secuencias obvias
		if (isSequential(lower)) {
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

	// Detectar secuencias (abc, 123, qwerty)
	private static boolean isSequential(String value) {
		for (int i = 0; i < value.length() - 2; i++) {
			char c1 = value.charAt(i);
			char c2 = value.charAt(i + 1);
			char c3 = value.charAt(i + 2);
			if (c2 == c1 + 1 && c3 == c2 + 1) {
				return true;  // Secuencia ascendente (abc, 123)
			}
			if (c2 == c1 - 1 && c3 == c2 - 1) {
				return true;  // Secuencia descendente (cba, 321)
			}
		}
		return false;
	}

}
