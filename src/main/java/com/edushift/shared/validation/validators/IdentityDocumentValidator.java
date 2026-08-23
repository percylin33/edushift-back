package com.edushift.shared.validation.validators;

import com.edushift.shared.validation.IdentityDocumentFields;
import com.edushift.shared.validation.IdentityDocumentRules;
import com.edushift.shared.validation.annotations.ValidIdentityDocument;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IdentityDocumentValidator
		implements ConstraintValidator<ValidIdentityDocument, IdentityDocumentFields> {

	@Override
	public boolean isValid(IdentityDocumentFields value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		if (IdentityDocumentRules.isValid(value.documentType(), value.documentNumber())) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(
				IdentityDocumentRules.violationMessage(value.documentType()))
				.addPropertyNode("documentNumber")
				.addConstraintViolation();
		return false;
	}
}
