package com.edushift.shared.validation;

import com.edushift.modules.students.entity.DocumentType;

/**
 * Normalises and validates identity-document numbers per {@link DocumentType}.
 *
 * <p>Peruvian DNI is strictly 8 digits — letters in that field are almost
 * always a data-entry mistake (e.g. a first name typed into the document box).
 */
public final class IdentityDocumentRules {

	private static final String ALNUM_DASH = "^[A-Za-z0-9-]+$";
	private static final String DNI = "^\\d{8}$";

	private IdentityDocumentRules() {
	}

	public static boolean isValid(DocumentType type, String rawNumber) {
		if (type == null || rawNumber == null) {
			return false;
		}
		String number = rawNumber.trim();
		if (number.isEmpty()) {
			return false;
		}
		return switch (type) {
			case DNI -> number.matches(DNI);
			case CE, PASSPORT, OTHER -> number.length() >= 4
					&& number.length() <= 20
					&& number.matches(ALNUM_DASH);
		};
	}

	public static String violationMessage(DocumentType type) {
		if (type == DocumentType.DNI) {
			return "documentNumber must be exactly 8 digits for DNI";
		}
		return "documentNumber must be 4-20 characters (letters, digits, dashes)";
	}
}
