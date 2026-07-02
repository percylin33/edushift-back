package com.edushift.modules.payments.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MercadoPagoPropertiesTest {

    @Test
    @DisplayName("defaults: sandbox, prod URLs, sentinel values")
    void defaults() {
        var p = new MercadoPagoProperties();
        assertThat(p.getAccessToken()).isEmpty();
        assertThat(p.getPublicKey()).isEmpty();
        assertThat(p.getMode()).isEqualTo("sandbox");
        assertThat(p.getApiBaseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(p.getSuccessUrl()).contains("/payments/success");
        assertThat(p.getFailureUrl()).contains("/payments/failure");
        assertThat(p.getWebhookUrl()).contains("/webhooks/mercadopago");
        assertThat(p.getWebhookSecret()).isEmpty();
        assertThat(p.isSandbox()).isTrue();
    }

    @Test
    @DisplayName("isSandbox true for sandbox, false for production")
    void sandboxToggle() {
        var p = new MercadoPagoProperties();
        p.setMode("SANDBOX"); // case-insensitive
        assertThat(p.isSandbox()).isTrue();
        p.setMode("production");
        assertThat(p.isSandbox()).isFalse();
        p.setMode("other");
        assertThat(p.isSandbox()).isFalse();
    }

    @Test
    @DisplayName("setters round-trip")
    void setters() {
        var p = new MercadoPagoProperties();
        p.setAccessToken("TEST-abc123");
        p.setPublicKey("TEST-pub");
        p.setMode("production");
        p.setApiBaseUrl("https://api.mercadopago.com");
        p.setSuccessUrl("https://app/success");
        p.setFailureUrl("https://app/failure");
        p.setWebhookUrl("https://api/wh");
        p.setWebhookSecret("hmacsecret");
        assertThat(p.getAccessToken()).isEqualTo("TEST-abc123");
        assertThat(p.getMode()).isEqualTo("production");
        assertThat(p.getWebhookSecret()).isEqualTo("hmacsecret");
    }
}