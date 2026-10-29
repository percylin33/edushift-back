package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiGenerationEntityTest {

    @Test
    @DisplayName("Feature enum includes application sheet suggestions")
    void features() {
        assertThat(AiGeneration.Feature.values()).containsExactly(AiGeneration.Feature.QUIZ_QUESTION_SUGGEST, AiGeneration.Feature.RUBRIC_SUGGEST, AiGeneration.Feature.SESSION_OUTLINE_SUGGEST, AiGeneration.Feature.APPLICATION_SHEET_SUGGEST, AiGeneration.Feature.OTHER);
        assertThat(AiGeneration.Feature.QUIZ_QUESTION_SUGGEST).isNotNull();
        assertThat(AiGeneration.Feature.RUBRIC_SUGGEST).isNotNull();
        assertThat(AiGeneration.Feature.SESSION_OUTLINE_SUGGEST).isNotNull();
        assertThat(AiGeneration.Feature.OTHER).isNotNull();
    }

    @Test
    @DisplayName("Status enum exposes 5 values")
    void status() {
        assertThat(AiGeneration.Status.values()).hasSize(5);
        assertThat(AiGeneration.Status.PENDING).isNotNull();
        assertThat(AiGeneration.Status.PROCESSING).isNotNull();
        assertThat(AiGeneration.Status.COMPLETED).isNotNull();
        assertThat(AiGeneration.Status.FAILED).isNotNull();
        assertThat(AiGeneration.Status.CANCELLED).isNotNull();
    }
}