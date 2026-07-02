package com.edushift.shared.validation.validators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidUuidValidatorTest {

    private final ValidUuidValidator validator = new ValidUuidValidator();

    @Test
    @DisplayName("returns true for null")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("returns true for valid UUID strings")
    void validUuids() {
        assertThat(validator.isValid("00000000-0000-0000-0000-000000000001", null)).isTrue();
        assertThat(validator.isValid("a1b2c3d4-e5f6-7890-abcd-ef1234567890", null)).isTrue();
        assertThat(validator.isValid("A1B2C3D4-E5F6-7890-ABCD-EF1234567890", null)).isTrue();
        assertThat(validator.isValid("ffffffff-ffff-ffff-ffff-ffffffffffff", null)).isTrue();
    }

    @Test
    @DisplayName("returns false for strings that are not 36 characters long")
    void wrongLength() {
        assertThat(validator.isValid("short", null)).isFalse();
        assertThat(validator.isValid("00000000-0000-0000-0000-00000000000", null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for malformed UUID strings")
    void malformedUuids() {
        assertThat(validator.isValid("not-a-uuid-at-all-0000-0000-00000000", null)).isFalse();
        assertThat(validator.isValid("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for UUID with wrong separator positions")
    void wrongSeparators() {
        assertThat(validator.isValid("000000000000-0000-0000-000000000000", null)).isFalse();
    }
}
