package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantAiSettingsEntityTest {

    @Test
    @DisplayName("defaults: aiEnabled=false, quotas null, no default model")
    void defaults() {
        var s = new TenantAiSettings();
        assertThat(s.isAiEnabled()).isFalse();
        assertThat(s.getDailyRequestQuota()).isNull();
        assertThat(s.getMonthlyTokenQuota()).isNull();
        assertThat(s.getDefaultModel()).isNull();
    }

    @Test
    @DisplayName("fields round-trip")
    void fields() {
        var s = new TenantAiSettings();
        s.setPublicUuid(UUID.randomUUID());
        s.setTenantId(UUID.randomUUID());
        s.setAiEnabled(true);
        s.setDailyRequestQuota(100);
        s.setMonthlyTokenQuota(1_000_000L);
        s.setDefaultModel("MiniMax/MiniMax-M2");
        assertThat(s.isAiEnabled()).isTrue();
        assertThat(s.getDailyRequestQuota()).isEqualTo(100);
        assertThat(s.getMonthlyTokenQuota()).isEqualTo(1_000_000L);
        assertThat(s.getDefaultModel()).isEqualTo("MiniMax/MiniMax-M2");
    }
}