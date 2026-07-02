package com.edushift.shared.validation.validators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsernameValidatorTest {

    private final UsernameValidator validator = new UsernameValidator();

    @Test
    @DisplayName("returns true for null")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("returns true for valid usernames")
    void validUsernames() {
        assertThat(validator.isValid("john_doe", null)).isTrue();
        assertThat(validator.isValid("alice.smith", null)).isTrue();
        assertThat(validator.isValid("user-42", null)).isTrue();
        assertThat(validator.isValid("abc", null)).isTrue();
        assertThat(validator.isValid("a123", null)).isTrue();
        assertThat(validator.isValid("test.user_1-ok", null)).isTrue();
    }

    @Test
    @DisplayName("returns false for usernames shorter than 3 characters")
    void tooShort() {
        assertThat(validator.isValid("ab", null)).isFalse();
        assertThat(validator.isValid("a", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for usernames longer than 30 characters")
    void tooLong() {
        assertThat(validator.isValid("a" + "b".repeat(30), null)).isFalse();
    }

    @Test
    @DisplayName("returns false when starting with non-alphanumeric character")
    void startsWithNonAlphanumeric() {
        assertThat(validator.isValid(".john", null)).isFalse();
        assertThat(validator.isValid("_john", null)).isFalse();
        assertThat(validator.isValid("-john", null)).isFalse();
    }

    @Test
    @DisplayName("returns false when containing invalid characters")
    void invalidCharacters() {
        assertThat(validator.isValid("john@doe", null)).isFalse();
        assertThat(validator.isValid("john doe", null)).isFalse();
        assertThat(validator.isValid("john#doe", null)).isFalse();
        assertThat(validator.isValid("john!doe", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for empty string")
    void emptyString() {
        assertThat(validator.isValid("", null)).isFalse();
    }
}
