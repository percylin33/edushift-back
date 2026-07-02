package com.edushift.modules.evaluations.rubric.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.rubric.entity.RubricLevel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RubricLevelTest {

    @Test
    @DisplayName("four canonical levels are declared")
    void allLevelsDeclared() {
        assertThat(RubricLevel.values())
                .containsExactly(
                        RubricLevel.EN_INICIO,
                        RubricLevel.EN_PROCESO,
                        RubricLevel.ESPERADO,
                        RubricLevel.SOBRESALIENTE);
    }

    @Test
    @DisplayName("order is monotonically increasing")
    void orderMonotonic() {
        var levels = List.of(RubricLevel.values());
        for (int i = 1; i < levels.size(); i++) {
            assertThat(levels.get(i).order()).isGreaterThan(levels.get(i - 1).order());
        }
    }

    @Test
    @DisplayName("displayName / shortCode / description are not blank")
    void metadataNotBlank() {
        for (RubricLevel level : RubricLevel.values()) {
            assertThat(level.displayName()).isNotBlank();
            assertThat(level.shortCode()).isNotBlank();
            assertThat(level.description()).isNotBlank();
        }
    }

    @Test
    @DisplayName("fromCode resolves canonical codes case-insensitively")
    void fromCodeCanonical() {
        assertThat(RubricLevel.fromCode("EN_INICIO")).contains(RubricLevel.EN_INICIO);
        assertThat(RubricLevel.fromCode("en_proceso")).contains(RubricLevel.EN_PROCESO);
        assertThat(RubricLevel.fromCode("ESPERADO")).contains(RubricLevel.ESPERADO);
        assertThat(RubricLevel.fromCode("Sobresaliente".toLowerCase()))
                .contains(RubricLevel.SOBRESALIENTE);
    }

    @Test
    @DisplayName("fromCode trims whitespace")
    void fromCodeTrims() {
        assertThat(RubricLevel.fromCode("  ESPERADO  ")).contains(RubricLevel.ESPERADO);
    }

    @Test
    @DisplayName("fromCode returns empty on unknown / null / blank")
    void fromCodeUnknown() {
        assertThat(RubricLevel.fromCode("CUSTOM")).isEqualTo(Optional.empty());
        assertThat(RubricLevel.fromCode(null)).isEqualTo(Optional.empty());
        assertThat(RubricLevel.fromCode("")).isEqualTo(Optional.empty());
        assertThat(RubricLevel.fromCode("   ")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("isCanonical mirrors fromCode")
    void isCanonical() {
        assertThat(RubricLevel.isCanonical("EN_INICIO")).isTrue();
        assertThat(RubricLevel.isCanonical("en_inicio")).isTrue();
        assertThat(RubricLevel.isCanonical("CUSTOM")).isFalse();
        assertThat(RubricLevel.isCanonical(null)).isFalse();
    }
}