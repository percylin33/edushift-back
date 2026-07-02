package com.edushift.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationCodesTest {

    @Test
    @DisplayName("all constants are non-null and non-empty")
    void allConstantsAreNonBlank() {
        getConstantValues().forEach(val -> {
            assertThat(val).isNotNull();
            assertThat(val).isNotEmpty();
        });
    }

    @Test
    @DisplayName("all constants are unique")
    void allConstantsAreUnique() {
        var values = getConstantValues().toList();
        assertThat(values).doesNotHaveDuplicates();
    }

    private static Stream<String> getConstantValues() {
        return Arrays.stream(ValidationCodes.class.getDeclaredFields())
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
