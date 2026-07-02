package com.edushift.shared.constants;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleNamesTest {

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
    @DisplayName("constants include expected module names")
    void containsExpected() {
        assertThat(ModuleNames.AUTH).isEqualTo("auth");
        assertThat(ModuleNames.TENANTS).isEqualTo("tenants");
        assertThat(ModuleNames.USERS).isEqualTo("users");
        assertThat(ModuleNames.STUDENTS).isEqualTo("students");
        assertThat(ModuleNames.TEACHERS).isEqualTo("teachers");
        assertThat(ModuleNames.ACADEMIC).isEqualTo("academic");
        assertThat(ModuleNames.EVALUATIONS).isEqualTo("evaluations");
        assertThat(ModuleNames.ATTENDANCE).isEqualTo("attendance");
        assertThat(ModuleNames.PAYMENTS).isEqualTo("payments");
        assertThat(ModuleNames.AI).isEqualTo("ai");
        assertThat(ModuleNames.NOTIFICATIONS).isEqualTo("notifications");
        assertThat(ModuleNames.REPORTS).isEqualTo("reports");
        assertThat(ModuleNames.ANALYTICS).isEqualTo("analytics");
        assertThat(ModuleNames.FILES).isEqualTo("files");
        assertThat(ModuleNames.AUDIT).isEqualTo("audit");
    }

    private static Stream<String> getConstantValues() {
        return Arrays.stream(ModuleNames.class.getDeclaredFields())
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
