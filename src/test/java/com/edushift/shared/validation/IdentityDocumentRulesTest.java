package com.edushift.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityDocumentRulesTest {

	@Test
	@DisplayName("DNI — accepts exactly 8 digits")
	void dniAcceptsEightDigits() {
		assertThat(IdentityDocumentRules.isValid(DocumentType.DNI, "12345678")).isTrue();
	}

	@Test
	@DisplayName("DNI — rejects letters (common data-entry mistake)")
	void dniRejectsLetters() {
		assertThat(IdentityDocumentRules.isValid(DocumentType.DNI, "percy")).isFalse();
		assertThat(IdentityDocumentRules.isValid(DocumentType.DNI, "1234567")).isFalse();
		assertThat(IdentityDocumentRules.isValid(DocumentType.DNI, "123456789")).isFalse();
	}

	@Test
	@DisplayName("CE — accepts alphanumeric 4-20 chars")
	void ceAcceptsAlphanumeric() {
		assertThat(IdentityDocumentRules.isValid(DocumentType.CE, "AB-1234")).isTrue();
	}
}
