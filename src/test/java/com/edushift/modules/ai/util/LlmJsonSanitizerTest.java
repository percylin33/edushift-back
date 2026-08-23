package com.edushift.modules.ai.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmJsonSanitizerTest {

    @Test
    @DisplayName("strips <think> prefix before JSON object")
    void stripsRedactedThinking() {
        String raw = "<think>planning rubric...</think>\n{\"name\":\"Rubrica\"}";
        assertThat(LlmJsonSanitizer.sanitize(raw)).isEqualTo("{\"name\":\"Rubrica\"}");
    }

    @Test
    @DisplayName("strips markdown json fence")
    void stripsMarkdownFence() {
        String raw = "```json\n{\"criteria\":[]}\n```";
        assertThat(LlmJsonSanitizer.sanitize(raw)).isEqualTo("{\"criteria\":[]}");
    }

    @Test
    @DisplayName("passes through clean JSON unchanged")
    void passthroughCleanJson() {
        String raw = "{\"questions\":[{\"prompt\":\"x\"}]}";
        assertThat(LlmJsonSanitizer.sanitize(raw)).isEqualTo(raw);
    }
}
