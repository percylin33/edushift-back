package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.TenantSlug;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Enforces the {@link TenantSlug} contract:
 * length 3-50, lowercase alphanumerics and hyphens, no leading/trailing or
 * consecutive hyphens.
 */
public class TenantSlugValidator implements ConstraintValidator<TenantSlug, CharSequence> {

	private static final Pattern PATTERN =
			Pattern.compile("^[a-z0-9](?:[a-z0-9]|-(?!-)){1,48}[a-z0-9]$");

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		String slug = value.toString();
		if (slug.length() < 3 || slug.length() > 50) {
			return false;
		}
		return PATTERN.matcher(slug).matches();
	}

}
