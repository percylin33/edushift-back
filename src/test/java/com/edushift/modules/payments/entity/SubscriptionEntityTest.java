package com.edushift.modules.payments.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionEntityTest {

    @Test
    @DisplayName("defaults: status=ACTIVE, currency=PEN, metadata={}")
    void defaults() {
        var s = new Subscription();
        assertThat(s.getStatus()).isEqualTo(Subscription.Status.ACTIVE);
        assertThat(s.getCurrency()).isEqualTo("PEN");
        assertThat(s.getMetadata()).isEqualTo("{}");
        assertThat(s.getStartAt()).isNotNull();
    }

    @Test
    @DisplayName("BillingPeriod + Status enums")
    void enums() {
        assertThat(Subscription.BillingPeriod.values()).hasSize(2);
        assertThat(Subscription.Status.values()).hasSize(3);
    }

    @Test
    @DisplayName("fields round-trip")
    void setters() {
        var s = new Subscription();
        s.setPublicUuid(UUID.randomUUID());
        s.setStudentId(UUID.randomUUID());
        s.setGuardianUserId(UUID.randomUUID());
        s.setPlanCode("BASIC_M");
        s.setAmountCents(25000L);
        s.setCurrency("USD");
        s.setBillingPeriod(Subscription.BillingPeriod.MONTHLY);
        s.setStatus(Subscription.Status.PAUSED);
        s.setStartAt(Instant.parse("2026-01-01T00:00:00Z"));
        s.setNextBillingAt(Instant.parse("2026-02-01T00:00:00Z"));
        s.setCancelledAt(Instant.parse("2026-12-31T00:00:00Z"));
        s.setMetadata("{\"foo\":\"bar\"}");

        assertThat(s.getPlanCode()).isEqualTo("BASIC_M");
        assertThat(s.getAmountCents()).isEqualTo(25000L);
        assertThat(s.getStatus()).isEqualTo(Subscription.Status.PAUSED);
    }
}