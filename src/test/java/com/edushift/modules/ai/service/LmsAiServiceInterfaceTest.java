package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke-level test for the {@link LmsAiService} public surface. The
 * full synchronous / asynchronous behaviour is covered by the existing
 * integration tests; this one only verifies the method shapes the
 * controllers rely on.
 */
class LmsAiServiceInterfaceTest {

    @Test
    @DisplayName("suggestQuizQuestions takes a SuggestQuizQuestionsRequest and returns SuggestQuizQuestionsResponse")
    void surface() throws Exception {
        var m = LmsAiService.class.getMethod("suggestQuizQuestions", SuggestQuizQuestionsRequest.class);
        assertThat(m.getReturnType()).isEqualTo(SuggestQuizQuestionsResponse.class);
    }

    @Test
    @DisplayName("runGeneration exists and returns SuggestQuizQuestionsResponse")
    void runGeneration() throws Exception {
        var m = LmsAiService.class.getMethod("runGeneration",
                com.edushift.modules.ai.entity.AiGeneration.class,
                com.edushift.modules.ai.llm.LlmClient.LlmRequest.class,
                SuggestQuizQuestionsRequest.class);
        assertThat(m.getReturnType()).isEqualTo(SuggestQuizQuestionsResponse.class);
    }

    @Test
    @DisplayName("SuggestQuizQuestionsRequest DTO stores the wire fields")
    void dto() {
        var req = new SuggestQuizQuestionsRequest("tema", 1, "TF");
        assertThat(req.topic()).isEqualTo("tema");
        assertThat(req.count()).isEqualTo(1);
        assertThat(req.questionType()).isEqualTo("TF");
    }
}