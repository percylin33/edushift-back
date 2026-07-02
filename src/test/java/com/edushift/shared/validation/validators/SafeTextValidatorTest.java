package com.edushift.shared.validation.validators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeTextValidatorTest {

    private final SafeTextValidator validator = new SafeTextValidator();

    @Test
    @DisplayName("returns true for null")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("returns true for plain text")
    void plainText() {
        assertThat(validator.isValid("Hello, world!", null)).isTrue();
        assertThat(validator.isValid("The quick brown fox jumps over the lazy dog.", null)).isTrue();
    }

    @Test
    @DisplayName("returns true for text with allowed whitespace")
    void allowedWhitespace() {
        assertThat(validator.isValid("line1\nline2\r\nline3", null)).isTrue();
        assertThat(validator.isValid("col1\tcol2", null)).isTrue();
    }

    @Test
    @DisplayName("returns false for text containing <")
    void angleBracketOpen() {
        assertThat(validator.isValid("<script>", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for text containing >")
    void angleBracketClose() {
        assertThat(validator.isValid("script>", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for text containing NUL byte")
    void nullByte() {
        assertThat(validator.isValid("bad\0char", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for text with non-printable control characters")
    void controlCharacters() {
        assertThat(validator.isValid("bell\u0007char", null)).isFalse();
        assertThat(validator.isValid("esc\u001Bchar", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for DEL character (0x7F)")
    void delCharacter() {
        assertThat(validator.isValid("del\u007Fchar", null)).isFalse();
    }

    @Test
    @DisplayName("returns true for empty string")
    void emptyString() {
        assertThat(validator.isValid("", null)).isTrue();
    }

    @Test
    @DisplayName("returns true for text with special characters that are safe")
    void safeSpecialCharacters() {
        assertThat(validator.isValid("¡Hola! ¿Cómo estás?", null)).isTrue();
        assertThat(validator.isValid("naïve café 北京", null)).isTrue();
    }
}
