package com.edushift.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationErrorCodesTest {

    @Test
    @DisplayName("resolve returns VALIDATION_ERROR for null input")
    void nullInput() {
        assertThat(ValidationErrorCodes.resolve(null)).isEqualTo(ValidationCodes.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("resolve returns VALIDATION_ERROR for blank input")
    void blankInput() {
        assertThat(ValidationErrorCodes.resolve("  ")).isEqualTo(ValidationCodes.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("resolve returns REQUIRED for NotNull")
    void notNull() {
        assertThat(ValidationErrorCodes.resolve("NotNull")).isEqualTo(ValidationCodes.REQUIRED);
    }

    @Test
    @DisplayName("resolve returns REQUIRED for NotBlank")
    void notBlank() {
        assertThat(ValidationErrorCodes.resolve("NotBlank")).isEqualTo(ValidationCodes.REQUIRED);
    }

    @Test
    @DisplayName("resolve returns REQUIRED for NotEmpty")
    void notEmpty() {
        assertThat(ValidationErrorCodes.resolve("NotEmpty")).isEqualTo(ValidationCodes.REQUIRED);
    }

    @Test
    @DisplayName("resolve returns INVALID_EMAIL for Email")
    void email() {
        assertThat(ValidationErrorCodes.resolve("Email")).isEqualTo(ValidationCodes.INVALID_EMAIL);
    }

    @Test
    @DisplayName("resolve returns INVALID_EMAIL for ValidEmail")
    void validEmail() {
        assertThat(ValidationErrorCodes.resolve("ValidEmail")).isEqualTo(ValidationCodes.INVALID_EMAIL);
    }

    @Test
    @DisplayName("resolve returns WEAK_PASSWORD for StrongPassword")
    void strongPassword() {
        assertThat(ValidationErrorCodes.resolve("StrongPassword")).isEqualTo(ValidationCodes.WEAK_PASSWORD);
    }

    @Test
    @DisplayName("resolve returns WEAK_PASSWORD for ValidPassword")
    void validPassword() {
        assertThat(ValidationErrorCodes.resolve("ValidPassword")).isEqualTo(ValidationCodes.WEAK_PASSWORD);
    }

    @Test
    @DisplayName("resolve returns INVALID_PHONE for PhoneNumber")
    void phoneNumber() {
        assertThat(ValidationErrorCodes.resolve("PhoneNumber")).isEqualTo(ValidationCodes.INVALID_PHONE);
    }

    @Test
    @DisplayName("resolve returns INVALID_TENANT_SLUG for TenantSlug")
    void tenantSlug() {
        assertThat(ValidationErrorCodes.resolve("TenantSlug")).isEqualTo(ValidationCodes.INVALID_TENANT_SLUG);
    }

    @Test
    @DisplayName("resolve returns INVALID_USERNAME for Username")
    void username() {
        assertThat(ValidationErrorCodes.resolve("Username")).isEqualTo(ValidationCodes.INVALID_USERNAME);
    }

    @Test
    @DisplayName("resolve returns UNSAFE_TEXT for SafeText")
    void safeText() {
        assertThat(ValidationErrorCodes.resolve("SafeText")).isEqualTo(ValidationCodes.UNSAFE_TEXT);
    }

    @Test
    @DisplayName("resolve returns INVALID_UUID for ValidUuid")
    void validUuid() {
        assertThat(ValidationErrorCodes.resolve("ValidUuid")).isEqualTo(ValidationCodes.INVALID_UUID);
    }

    @Test
    @DisplayName("resolve returns INVALID_ENUM_VALUE for EnumValue")
    void enumValue() {
        assertThat(ValidationErrorCodes.resolve("EnumValue")).isEqualTo(ValidationCodes.INVALID_ENUM_VALUE);
    }

    @Test
    @DisplayName("resolve returns VALIDATION_ERROR for unknown annotation")
    void unknownAnnotation() {
        assertThat(ValidationErrorCodes.resolve("UnknownAnnotation")).isEqualTo(ValidationCodes.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("resolve returns correct codes for standard jakarta annotations")
    void standardAnnotations() {
        assertThat(ValidationErrorCodes.resolve("Null")).isEqualTo("MUST_BE_NULL");
        assertThat(ValidationErrorCodes.resolve("Pattern")).isEqualTo(ValidationCodes.PATTERN_MISMATCH);
        assertThat(ValidationErrorCodes.resolve("Digits")).isEqualTo("INVALID_NUMBER_FORMAT");
        assertThat(ValidationErrorCodes.resolve("Size")).isEqualTo(ValidationCodes.SIZE_OUT_OF_RANGE);
        assertThat(ValidationErrorCodes.resolve("Min")).isEqualTo(ValidationCodes.VALUE_TOO_SMALL);
        assertThat(ValidationErrorCodes.resolve("Max")).isEqualTo(ValidationCodes.VALUE_TOO_LARGE);
        assertThat(ValidationErrorCodes.resolve("Future")).isEqualTo("MUST_BE_FUTURE");
        assertThat(ValidationErrorCodes.resolve("Past")).isEqualTo("MUST_BE_PAST");
        assertThat(ValidationErrorCodes.resolve("AssertTrue")).isEqualTo("MUST_BE_TRUE");
        assertThat(ValidationErrorCodes.resolve("AssertFalse")).isEqualTo("MUST_BE_FALSE");
    }
}
