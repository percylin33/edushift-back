package com.edushift.shared.validation.validators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantSlugValidatorTest {

    private final TenantSlugValidator validator = new TenantSlugValidator();

    @Test
    @DisplayName("returns true for null")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("returns true for valid slugs")
    void validSlugs() {
        assertThat(validator.isValid("acme", null)).isTrue();
        assertThat(validator.isValid("school-42", null)).isTrue();
        assertThat(validator.isValid("edu-shift-pe", null)).isTrue();
        assertThat(validator.isValid("a0b1c2", null)).isTrue();
        assertThat(validator.isValid("abc", null)).isTrue();
    }

    @Test
    @DisplayName("returns false for slugs shorter than 3 characters")
    void tooShort() {
        assertThat(validator.isValid("ab", null)).isFalse();
        assertThat(validator.isValid("a", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for slugs longer than 50 characters")
    void tooLong() {
        assertThat(validator.isValid("a" + "b".repeat(50), null)).isFalse();
    }

    @Test
    @DisplayName("returns false for slugs with uppercase letters")
    void uppercaseNotAllowed() {
        assertThat(validator.isValid("Acme", null)).isFalse();
        assertThat(validator.isValid("SCHOOL", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for slugs starting with hyphen")
    void startsWithHyphen() {
        assertThat(validator.isValid("-acme", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for slugs ending with hyphen")
    void endsWithHyphen() {
        assertThat(validator.isValid("acme-", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for consecutive hyphens")
    void consecutiveHyphens() {
        assertThat(validator.isValid("school--42", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for empty string")
    void emptyString() {
        assertThat(validator.isValid("", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for slugs with special characters")
    void specialCharacters() {
        assertThat(validator.isValid("school_42", null)).isFalse();
        assertThat(validator.isValid("school@42", null)).isFalse();
        assertThat(validator.isValid("school 42", null)).isFalse();
    }
}
