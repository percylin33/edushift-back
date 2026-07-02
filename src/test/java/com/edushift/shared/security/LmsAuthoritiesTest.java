package com.edushift.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LmsAuthoritiesTest {

    @Test
    @DisplayName("all authority constants are non-null and non-empty")
    void allConstantsAreNonBlank() {
        getAuthorityValues().forEach(auth -> {
            assertThat(auth).isNotNull();
            assertThat(auth).isNotEmpty();
        });
    }

    @Test
    @DisplayName("all authority constants start with LMS_")
    void allStartWithLmsPrefix() {
        getAuthorityValues().forEach(auth ->
                assertThat(auth).startsWith("LMS_")
        );
    }

    @Test
    @DisplayName("all authority constants are unique (no duplicates)")
    void allConstantsAreUnique() {
        var values = getAuthorityValues().toList();
        assertThat(values).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("authority count matches expected number of constants")
    void authorityCount() {
        assertThat(getAuthorityValues()).hasSize(14);
    }

    private static Stream<String> getAuthorityValues() {
        return Arrays.stream(LmsAuthorities.class.getDeclaredFields())
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
