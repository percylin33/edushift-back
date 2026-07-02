package com.edushift.shared.constants;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggerNamesTest {

    @Test
    @DisplayName("all constants are non-null and non-blank")
    void allConstantsAreNonBlank() {
        getConstantValues().forEach(val -> {
            assertThat(val).isNotNull();
            assertThat(val).isNotBlank();
        });
    }

    @Test
    @DisplayName("all constants start with edushift.")
    void allStartWithPrefix() {
        getConstantValues().forEach(val ->
                assertThat(val).startsWith("edushift.")
        );
    }

    @Test
    @DisplayName("all constants are unique")
    void allConstantsAreUnique() {
        var values = getConstantValues().toList();
        assertThat(values).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("constants include expected loggers")
    void containsExpected() {
        assertThat(LoggerNames.SECURITY).isEqualTo("edushift.security");
        assertThat(LoggerNames.REQUESTS).isEqualTo("edushift.requests");
        assertThat(LoggerNames.EXCEPTIONS).isEqualTo("edushift.exceptions");
        assertThat(LoggerNames.AI).isEqualTo("edushift.ai");
        assertThat(LoggerNames.AUDIT).isEqualTo("edushift.audit");
    }

    private static Stream<String> getConstantValues() {
        return Arrays.stream(LoggerNames.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
