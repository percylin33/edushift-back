package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.annotations.EnumValue;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that the given string matches one of the constants declared by
 * the enum referenced in {@link EnumValue#enumClass()}. The valid set is
 * cached at {@code initialize()} time so each invocation is O(1).
 * <p>
 * Injects the allowed values into the violation message via the
 * {@code {values}} interpolation placeholder.
 */
public class EnumValueValidator implements ConstraintValidator<EnumValue, CharSequence> {

	private Set<String> acceptedValues;

	private boolean ignoreCase;

	private String renderedValues;

	@Override
	public void initialize(EnumValue ann) {
		this.ignoreCase = ann.ignoreCase();
		Enum<?>[] constants = ann.enumClass().getEnumConstants();
		this.acceptedValues = Arrays.stream(constants)
				.map(Enum::name)
				.map(name -> ignoreCase ? name.toLowerCase() : name)
				.collect(Collectors.toUnmodifiableSet());
		this.renderedValues = Arrays.stream(constants)
				.map(Enum::name)
				.collect(Collectors.joining(", "));
	}

	@Override
	public boolean isValid(CharSequence value, ConstraintValidatorContext ctx) {
		if (value == null) {
			return true;
		}
		String candidate = ignoreCase ? value.toString().toLowerCase() : value.toString();
		if (acceptedValues.contains(candidate)) {
			return true;
		}
		String template = ctx.getDefaultConstraintMessageTemplate().replace("{values}", renderedValues);
		ctx.disableDefaultConstraintViolation();
		ctx.buildConstraintViolationWithTemplate(template).addConstraintViolation();
		return false;
	}

}
