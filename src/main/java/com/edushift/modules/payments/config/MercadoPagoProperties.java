package com.edushift.modules.payments.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MercadoPago integration config (Sprint 10 / BE-10.2, ADR-10.1).
 *
 * <p>Sandbox by default (MP lets you create a sandbox account at
 * {@code https://www.mercadopago.com.pe/developers/panel}). Real
 * production tokens are loaded from env vars in production; we never
 * hardcode them in {@code application.properties}.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "app.payments.mercadopago")
@Getter
@Setter
public class MercadoPagoProperties {

    /** Access token. In dev: sandbox token. In prod: from env. */
    private String accessToken = "";

    /** Public key for the FE SDK (Checkout Pro). */
    private String publicKey = "";

    /** "sandbox" or "production". */
    private String mode = "sandbox";

    /**
     * Base URL for the MP REST API. Sandbox uses the
     * {@code .sandbox.} host; production uses the bare host.
     */
    private String apiBaseUrl = "https://api.mercadopago.com";

    /** Where MP redirects after a successful payment. */
    private String successUrl = "https://app.edushift.com/payments/success";

    /** Where MP redirects after a rejected/cancelled payment. */
    private String failureUrl = "https://app.edushift.com/payments/failure";

    /** Where MP sends IPN/webhook notifications. Must be HTTPS in prod. */
    private String webhookUrl = "https://api.edushift.com/api/v1/webhooks/mercadopago";

    /**
     * HMAC secret for webhook signature verification (header
     * {@code x-signature}). If blank, we log a warning and accept
     * unsigned webhooks — NEVER in production.
     */
    private String webhookSecret = "";

    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(mode);
    }
}
