package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantAiUsageEntityTest {

    @Test
    @DisplayName("defaults: every counter at 0")
    void defaults() {
        var u = new TenantAiUsage();
        assertThat(u.getRequestCount()).isZero();
        assertThat(u.getSuccessCount()).isZero();
        assertThat(u.getFailedCount()).isZero();
        assertThat(u.getTokensInTotal()).isZero();
        assertThat(u.getTokensOutTotal()).isZero();
        assertThat(u.getUsageDay()).isNull();
    }

    @Test
    @DisplayName("fields round-trip")
    void fields() {
        var u = new TenantAiUsage();
        u.setTenantId(UUID.randomUUID());
        u.setUsageDay(LocalDate.of(2026, 1, 1));
        u.setRequestCount(10);
        u.setSuccessCount(8);
        u.setFailedCount(2);
        u.setTokensInTotal(1500L);
        u.setTokensOutTotal(2200L);
        assertThat(u.getUsageDay()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(u.getRequestCount()).isEqualTo(10);
        assertThat(u.getTokensInTotal()).isEqualTo(1500L);
    }
}