package com.edushift.modules.evaluations.rubric.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RubricErrorCodesTest {

    @Test
    @DisplayName("error codes are stable constants")
    void codesAreStable() {
        assertThat(RubricErrorCodes.RUB_NAME_EXISTS).isEqualTo("RUB_NAME_EXISTS");
        assertThat(RubricErrorCodes.RUB_SYSTEM_READ_ONLY).isEqualTo("RUB_SYSTEM_READ_ONLY");
        assertThat(RubricErrorCodes.RUB_CRITERIA_WEIGHT_SUM).isEqualTo("RUB_CRITERIA_WEIGHT_SUM");
        assertThat(RubricErrorCodes.RUB_CRITERIA_COUNT).isEqualTo("RUB_CRITERIA_COUNT");
        assertThat(RubricErrorCodes.RUB_LEVELS_COUNT).isEqualTo("RUB_LEVELS_COUNT");
        assertThat(RubricErrorCodes.RUB_LEVEL_CODE_DUPLICATE).isEqualTo("RUB_LEVEL_CODE_DUPLICATE");
        assertThat(RubricErrorCodes.RUB_LEVEL_UNKNOWN).isEqualTo("RUB_LEVEL_UNKNOWN");
        assertThat(RubricErrorCodes.RUB_CRITERION_KEY_DUPLICATE).isEqualTo("RUB_CRITERION_KEY_DUPLICATE");
        assertThat(RubricErrorCodes.RUB_DESCRIPTOR_DUPLICATE).isEqualTo("RUB_DESCRIPTOR_DUPLICATE");
        assertThat(RubricErrorCodes.RUB_CANNOT_FORK_NON_SYSTEM).isEqualTo("RUB_CANNOT_FORK_NON_SYSTEM");
        assertThat(RubricErrorCodes.RUB_PARENT_NOT_FOUND).isEqualTo("RUB_PARENT_NOT_FOUND");
    }

    @Test
    @DisplayName("all codes are unique")
    void allCodesAreUnique() {
        var codes = java.util.List.of(
                RubricErrorCodes.RUB_NAME_EXISTS,
                RubricErrorCodes.RUB_SYSTEM_READ_ONLY,
                RubricErrorCodes.RUB_CRITERIA_WEIGHT_SUM,
                RubricErrorCodes.RUB_CRITERIA_COUNT,
                RubricErrorCodes.RUB_LEVELS_COUNT,
                RubricErrorCodes.RUB_LEVEL_CODE_DUPLICATE,
                RubricErrorCodes.RUB_LEVEL_UNKNOWN,
                RubricErrorCodes.RUB_CRITERION_KEY_DUPLICATE,
                RubricErrorCodes.RUB_DESCRIPTOR_DUPLICATE,
                RubricErrorCodes.RUB_CANNOT_FORK_NON_SYSTEM,
                RubricErrorCodes.RUB_PARENT_NOT_FOUND);
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("class is not instantiable")
    void notInstantiable() throws Exception {
        var ctor = RubricErrorCodes.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.getModifiers())
                .matches(java.lang.reflect.Modifier::isPrivate);
    }
}