package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiGenerationEntityTest {

    @Test
    @DisplayName("Feature enum exposes 4 values")
    void features() {
        assertThat(AiGeneration.Feature.values()).hasSize(4);
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