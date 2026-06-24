package com.edushift.modules.payments.controller;

import com.edushift.infrastructure.ratelimit.SimpleRateLimiter;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.payments.config.MercadoPagoProperties;
import com.edushift.modules.payments.service.PaymentService;
import com.edushift.shared.constants.ModuleNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MercadoPago IPN webhook (Sprint 10 / BE-10.2 + SEC-10.1).
 *
 * <p>Public endpoint (no JWT) — MP can't authenticate. Security
 * is layered:</p>
 * <ol>
 *   <li><b>Signature verification</b> — {@code x-signature} HMAC against
 *       {@code app.payments.mercadopago.webhookSecret} (configurable).
 *       In dev the secret is blank; we log a warning and accept. NEVER
 *       deploy to prod without setting it.</li>
 *   <li><b>Rate limit</b> — {@code SimpleRateLimiter} caps at
 *       {@code app.payments.webhook.max-per-min} req/min per IP. MP
 *       sends 1-5 webhooks per payment, so 60/min is comfortable
 *       headroom.</li>
 *   <li><b>Idempotency</b> — the unique index
 *       {@code (tenant_id, provider, external_id)} makes webhook
 *       replays safe. We fetch the canonical status from MP and
 *       upsert.</li>
 *   <li><b>Audit log</b> — every accepted webhook logs an
 *       {@link AuditLogger} entry with the MP payment id + type +
 *       remote IP. No PII.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/webhooks/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final PaymentService paymentService;
    private final MercadoPagoProperties props;
    private final ObjectMapper objectMapper;
    private final SimpleRateLimiter rateLimiter;
    private final AuditLogger audit;

    @Value("${app.payments.webhook.max-per-min:60}")
    private int maxPerMin;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handle(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestBody String rawBody,
            HttpServletRequest request) {

        String remoteIp = request.getRemoteAddr();
        log.info("[MP webhook] requestId={} ip={} signature?={}",
                requestId, remoteIp, signature != null);

        // 1) Rate limit (SEC-10.1).
        if (!rateLimiter.allow("mp:" + remoteIp, maxPerMin, 60_000)) {
            log.warn("[MP webhook] rate limit exceeded ip={}", remoteIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "rate limit"));
        }

        // 2) Signature verification.
        if (!verifySignature(rawBody, signature)) {
            log.warn("[MP webhook] bad or missing signature for requestId={}", requestId);
            audit.log(AuditAction.ACCESS_DENIED, "mercadopago", null,
                    "MP webhook bad signature",
                    Map.of("ip", remoteIp, "requestId", requestId == null ? "" : requestId),
                    ModuleNames.PAYMENTS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid signature"));
        }

        // 3) Parse the payload.
        String dataId;
        String type;
        try {
            var node = objectMapper.readTree(rawBody);
            type = node.path("type").asText("");
            dataId = node.path("data").path("id").asText("");
        } catch (Exception e) {
            log.warn("[MP webhook] malformed JSON body: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "malformed body"));
        }
        if (dataId.isBlank() || type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing data.id or type"));
        }

        // 4) Process (idempotent).
        try {
            paymentService.handleMercadoPagoWebhook(dataId, type, rawBody);
            audit.log(AuditAction.CREATE, "mercadopago", null,
                    "MP webhook accepted",
                    Map.of("dataId", dataId, "type", type,
                            "ip", remoteIp,
                            "requestId", requestId == null ? "" : requestId),
                    ModuleNames.PAYMENTS);
        } catch (Exception e) {
            log.error("[MP webhook] processing failed for dataId={} type={}: {}",
                    dataId, type, e.getMessage(), e);
            audit.log(AuditAction.CUSTOM, "mercadopago", null,
                    "MP webhook processing failed",
                    Map.of("dataId", dataId, "type", type,
                            "ip", remoteIp,
                            "error", e.getMessage() == null ? "" : e.getMessage()),
                    ModuleNames.PAYMENTS);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "processing failed"));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private boolean verifySignature(String rawBody, String signatureHeader) {
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("[MP webhook] webhook-secret is blank; accepting without verification. DO NOT USE IN PROD.");
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) hex.append(String.format("%02x", b));
            String expected = hex.toString();
            return constantTimeEquals(expected, signatureHeader);
        } catch (Exception e) {
            log.error("[MP webhook] HMAC failure: {}", e.getMessage());
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
