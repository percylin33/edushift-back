package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiChatMessageEntityTest {

    @Test
    @DisplayName("Role enum: USER, ASSISTANT, SYSTEM")
    void roleEnum() {
        assertThat(AiChatMessage.Role.values()).hasSize(3);
        assertThat(AiChatMessage.Role.USER).isNotNull();
        assertThat(AiChatMessage.Role.ASSISTANT).isNotNull();
        assertThat(AiChatMessage.Role.SYSTEM).isNotNull();
    }

    @Test
    @DisplayName("Status enum exposes 5 lifecycle states")
    void statusEnum() {
        assertThat(AiChatMessage.Status.values()).hasSize(5);
        assertThat(AiChatMessage.Status.STREAMING).isNotNull();
        assertThat(AiChatMessage.Status.COMPLETED).isNotNull();
        assertThat(AiChatMessage.Status.FAILED).isNotNull();
        assertThat(AiChatMessage.Status.CANCELLED).isNotNull();
    }

    @Test
    @DisplayName("fields round-trip including input/output hashes (SEC-8.1)")
    void fields() {
        var m = new AiChatMessage();
        m.setPublicUuid(UUID.randomUUID());
        m.setChatSessionId(UUID.randomUUID());
        m.setRole(AiChatMessage.Role.USER);
        m.setContent("Hello AI");
        m.setStatus(AiChatMessage.Status.COMPLETED);
        m.setModelUsed("MiniMax/MiniMax-M2");
        m.setPromptTokens(10);
        m.setResponseTokens(20);
        m.setLatencyMs(150);
        m.setInputHash("abc123");
        m.setOutputHash("def456");
        assertThat(m.getRole()).isEqualTo(AiChatMessage.Role.USER);
        assertThat(m.getInputHash()).isEqualTo("abc123");
        assertThat(m.getLatencyMs()).isEqualTo(150);
    }
}