package com.edushift.modules.evaluations.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationKindTest {

    @Test
    @DisplayName("all five kinds are declared")
    void allKindsDeclared() {
        assertThat(EvaluationKind.values())
                .containsExactly(
                        EvaluationKind.TASK,
                        EvaluationKind.QUIZ,
                        EvaluationKind.EXAM,
                        EvaluationKind.RUBRIC,
                        EvaluationKind.COMPETENCY);
    }

    @Test
    @DisplayName("valueOf round-trip")
    void valueOf() {
        assertThat(EvaluationKind.valueOf("TASK")).isEqualTo(EvaluationKind.TASK);
        assertThat(EvaluationKind.valueOf("COMPETENCY")).isEqualTo(EvaluationKind.COMPETENCY);
    }
}