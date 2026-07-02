package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiChatSessionEntityTest {

    @Test
    @DisplayName("defaults: status=ACTIVE, counters=0, title sentinel")
    void defaults() {
        var s = new AiChatSession();
        assertThat(s.getStatus()).isEqualTo(AiChatSession.Status.ACTIVE);
        assertThat(s.getMessageCount()).isZero();
        assertThat(s.getTotalTokensIn()).isZero();
        assertThat(s.getTotalTokensOut()).isZero();
        assertThat(s.getTitle()).isEqualTo("Nueva conversacion");
    }

    @Test
    @DisplayName("recordMessage bumps counters + stamps lastMessageAt")
    void recordMessage() {
        var s = new AiChatSession();
        s.recordMessage(10, 20);
        s.recordMessage(5, 7);
        assertThat(s.getMessageCount()).isEqualTo(2);
        assertThat(s.getTotalTokensIn()).isEqualTo(15);
        assertThat(s.getTotalTokensOut()).isEqualTo(27);
        assertThat(s.getLastMessageAt()).isNotNull();
        assertThat(s.getLastMessageAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Status enum exposes 3 lifecycle states")
    void statusEnum() {
        assertThat(AiChatSession.Status.values()).hasSize(3);
    }

    @Test
    @DisplayName("publicUuid populated on first save")
    void publicUuid() {
        var s = new AiChatSession();
        s.setPublicUuid(UUID.randomUUID());
        s.setUserId(UUID.randomUUID());
        assertThat(s.getPublicUuid()).isNotNull();
    }
}