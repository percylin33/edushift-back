package com.edushift.modules.evaluations.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationScaleTest {

    @Test
    @DisplayName("all four scales are declared")
    void allScalesDeclared() {
        assertThat(EvaluationScale.values())
                .containsExactly(
                        EvaluationScale.SCORE_0_20,
                        EvaluationScale.LITERAL_AD,
                        EvaluationScale.LITERAL_NA,
                        EvaluationScale.LITERAL_A_B_C_D);
    }

    @Test
    @DisplayName("numeric scale is the only SCORE_*")
    void onlyOneNumeric() {
        long numericCount = java.util.Arrays.stream(EvaluationScale.values())
                .filter(s -> s.name().startsWith("SCORE_"))
                .count();
        assertThat(numericCount).isEqualTo(1);
    }
}