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
			// Rechazar caracteres de control ASCII
			if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
				return false;
			}
			// Rechazar DEL y caracteres de control C1
			if (c == 0x7F || (c >= 0x80 && c <= 0x9F)) {
				return false;
			}
			// Rechazar caracteres Unicode peligrosos
			if (c == 0x202E || c == 0x202D) {  // RTL/LTR override
				return false;
			}
			if (c == 0x200E || c == 0x200F) {  // LTR/RTL mark
				return false;
			}
			if (c == 0xFEFF) {  // Zero-width no-break space (BOM)
				return false;
			}
		}
		return true;
	}

}
