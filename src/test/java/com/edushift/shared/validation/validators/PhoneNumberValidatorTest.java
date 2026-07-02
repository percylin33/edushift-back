package com.edushift.shared.validation.validators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumberValidatorTest {

    private final PhoneNumberValidator validator = new PhoneNumberValidator();

    @Test
    @DisplayName("returns true for null")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("returns true for valid E.164 numbers")
    void validNumbers() {
        assertThat(validator.isValid("+1234567890", null)).isTrue();
        assertThat(validator.isValid("1234567890", null)).isTrue();
        assertThat(validator.isValid("+5491123456789", null)).isTrue();
        assertThat(validator.isValid("+1 (555) 123-4567", null)).isTrue();
        assertThat(validator.isValid("+44 20 7946 0958", null)).isTrue();
        assertThat(validator.isValid("+1-800-555-0199", null)).isTrue();
        assertThat(validator.isValid("+1.800.555.0199", null)).isTrue();
        assertThat(validator.isValid("+1(800)555-0199", null)).isTrue();
    }

    @Test
    @DisplayName("returns false for empty string after stripping separators")
    void emptyAfterStrip() {
        assertThat(validator.isValid("()-()", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for numbers with leading zero")
    void leadingZero() {
        assertThat(validator.isValid("+0123456789", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for too short numbers")
    void tooShort() {
        assertThat(validator.isValid("+123", null)).isFalse();
        assertThat(validator.isValid("123456", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for numbers with letters")
    void lettersNotAllowed() {
        assertThat(validator.isValid("+12345ABC", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for blank string")
    void blank() {
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid("   ", null)).isFalse();
    }
}
