package com.edushift.shared.validation.validators;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.shared.validation.annotations.StrongPassword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @BeforeEach
    void setUp() {
        validator.initialize(defaultAnnotation());
    }

    @Test
    @DisplayName("returns true for null")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("returns true for a valid password meeting all requirements")
    void validPassword() {
        assertThat(validator.isValid("P@ssw0rd!", null)).isTrue();
        assertThat(validator.isValid("Str0ng!Pass", null)).isTrue();
        assertThat(validator.isValid("Abcd1234#", null)).isTrue();
    }

    @Test
    @DisplayName("returns false for a password that is too short")
    void tooShort() {
        assertThat(validator.isValid("Ab1!c", null)).isFalse();
    }

    @Test
    @DisplayName("returns false for a password that is too long")
    void tooLong() {
        assertThat(validator.isValid("A1!" + "a".repeat(70), null)).isFalse();
    }

    @Test
    @DisplayName("returns false when missing uppercase")
    void missingUppercase() {
        assertThat(validator.isValid("passw0rd!", null)).isFalse();
    }

    @Test
    @DisplayName("returns false when missing lowercase")
    void missingLowercase() {
        assertThat(validator.isValid("PASSWORD1!", null)).isFalse();
    }

    @Test
    @DisplayName("returns false when missing digit")
    void missingDigit() {
        assertThat(validator.isValid("Password!", null)).isFalse();
    }

    @Test
    @DisplayName("returns false when missing special character")
    void missingSpecial() {
        assertThat(validator.isValid("Password1", null)).isFalse();
    }

    @Test
    @DisplayName("honours custom minLength")
    void customMinLength() {
        validator.initialize(annotationWith(10, 72, true, true, true, true));
        assertThat(validator.isValid("Ab1!defgh", null)).isFalse();
        assertThat(validator.isValid("Ab1!defghij", null)).isTrue();
    }

    @Test
    @DisplayName("honours maxLength")
    void customMaxLength() {
        validator.initialize(annotationWith(8, 10, true, true, true, true));
        assertThat(validator.isValid("Ab1!defghij", null)).isFalse();
        assertThat(validator.isValid("Ab1!defgh", null)).isTrue();
    }

    @Test
    @DisplayName("can disable uppercase requirement")
    void noUppercaseRequired() {
        validator.initialize(annotationWith(8, 72, false, true, true, true));
        assertThat(validator.isValid("passw0rd!", null)).isTrue();
    }

    @Test
    @DisplayName("can disable lowercase requirement")
    void noLowercaseRequired() {
        validator.initialize(annotationWith(8, 72, true, false, true, true));
        assertThat(validator.isValid("PASSW0RD!", null)).isTrue();
    }

    @Test
    @DisplayName("can disable digit requirement")
    void noDigitRequired() {
        validator.initialize(annotationWith(8, 72, true, true, false, true));
        assertThat(validator.isValid("Password!", null)).isTrue();
    }

    @Test
    @DisplayName("can disable special character requirement")
    void noSpecialRequired() {
        validator.initialize(annotationWith(8, 72, true, true, true, false));
        assertThat(validator.isValid("Password1", null)).isTrue();
    }

    private static StrongPassword defaultAnnotation() {
        return annotationWith(8, 72, true, true, true, true);
    }

    private static StrongPassword annotationWith(
            int minLength, int maxLength, boolean upper, boolean lower, boolean digit, boolean special) {
        return new StrongPassword() {
            @Override public int minLength() { return minLength; }
            @Override public int maxLength() { return maxLength; }
            @Override public boolean requireUppercase() { return upper; }
            @Override public boolean requireLowercase() { return lower; }
            @Override public boolean requireDigit() { return digit; }
            @Override public boolean requireSpecial() { return special; }
            @Override public String message() { return "{edushift.validation.StrongPassword.message}"; }
            @Override public Class<?>[] groups() { return new Class[0]; }
            @Override public Class<? extends jakarta.validation.Payload>[] payload() { return new Class[0]; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return StrongPassword.class;
            }
        };
    }
}
