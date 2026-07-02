package com.edushift.modules.evaluations.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationStatusTest {

    @Test
    @DisplayName("all three states are declared")
    void allStatesDeclared() {
        assertThat(EvaluationStatus.values())
                .containsExactly(
                        EvaluationStatus.DRAFT,
                        EvaluationStatus.PUBLISHED,
                        EvaluationStatus.CLOSED);
    }

    @Test
    @DisplayName("CLOSED is terminal")
    void closedIsTerminal() {
        assertThat(EvaluationStatus.CLOSED.isTerminal()).isTrue();
        assertThat(EvaluationStatus.DRAFT.isTerminal()).isFalse();
        assertThat(EvaluationStatus.PUBLISHED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("legalNext — DRAFT can only go to PUBLISHED")
    void legalNextFromDraft() {
        Set<EvaluationStatus> next = EvaluationStatus.DRAFT.legalNext();
        assertThat(next).containsExactly(EvaluationStatus.PUBLISHED);
    }

    @Test
    @DisplayName("legalNext — PUBLISHED can only go to CLOSED")
    void legalNextFromPublished() {
        Set<EvaluationStatus> next = EvaluationStatus.PUBLISHED.legalNext();
        assertThat(next).containsExactly(EvaluationStatus.CLOSED);
    }

    @Test
    @DisplayName("legalNext — CLOSED has no transitions")
    void legalNextFromClosed() {
        Set<EvaluationStatus> next = EvaluationStatus.CLOSED.legalNext();
        assertThat(next).isEmpty();
        assertThat(next).isEqualTo(EnumSet.noneOf(EvaluationStatus.class));
    }
}