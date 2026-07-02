package com.edushift.shared.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifiersTest {

    @Test
    @DisplayName("tryParse returns Optional of UUID for valid UUID string")
    void tryParseValid() {
        var uuid = UUID.randomUUID();
        var result = Identifiers.tryParse(uuid.toString());
        assertThat(result).isPresent().contains(uuid);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("tryParse returns empty for null, blank, or empty strings")
    void tryParseInvalid(String value) {
        assertThat(Identifiers.tryParse(value)).isEmpty();
    }

    @Test
    @DisplayName("tryParse returns empty for malformed string")
    void tryParseMalformed() {
        assertThat(Identifiers.tryParse("not-a-uuid")).isEmpty();
    }

    @Test
    @DisplayName("isValid returns true for valid UUID string")
    void isValidValid() {
        assertThat(Identifiers.isValid(UUID.randomUUID().toString())).isTrue();
    }

    @Test
    @DisplayName("isValid returns false for invalid UUID string")
    void isValidInvalid() {
        assertThat(Identifiers.isValid("bad-uuid")).isFalse();
    }

    @Test
    @DisplayName("isV7 returns true for v7 UUID")
    void isV7True() {
        var v7 = UuidV7.create();
        assertThat(Identifiers.isV7(v7)).isTrue();
    }

    @Test
    @DisplayName("isV7 returns false for non-v7 UUID")
    void isV7False() {
        var random = UUID.randomUUID();
        assertThat(Identifiers.isV7(random)).isFalse();
    }

    @Test
    @DisplayName("isV7 returns false for null")
    void isV7Null() {
        assertThat(Identifiers.isV7(null)).isFalse();
    }
}
