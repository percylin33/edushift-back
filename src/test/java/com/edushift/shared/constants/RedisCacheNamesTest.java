package com.edushift.shared.constants;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RedisCacheNamesTest {

    @Test
    @DisplayName("all constants are non-null and non-blank")
    void allConstantsAreNonBlank() {
        getConstantValues().forEach(val -> {
            assertThat(val).isNotNull();
            assertThat(val).isNotBlank();
        });
    }

    @Test
    @DisplayName("all constants are unique")
    void allConstantsAreUnique() {
        var values = getConstantValues().toList();
        assertThat(values).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("constants include expected cache names")
    void containsExpected() {
        assertThat(RedisCacheNames.DASHBOARDS).isEqualTo("dashboards");
        assertThat(RedisCacheNames.PERMISSIONS).isEqualTo("permissions");
        assertThat(RedisCacheNames.TENANT_CONFIG).isEqualTo("tenant-config");
        assertThat(RedisCacheNames.AI_CONTEXT).isEqualTo("ai-context");
    }

    private static Stream<String> getConstantValues() {
        return Arrays.stream(RedisCacheNames.class.getDeclaredFields())
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
