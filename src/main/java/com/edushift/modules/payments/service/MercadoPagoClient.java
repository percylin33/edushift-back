package com.edushift.modules.payments.service;

import com.edushift.modules.payments.config.MercadoPagoProperties;
import com.edushift.modules.payments.exception.PaymentFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * MercadoPago REST client (Sprint 10 / BE-10.2, ADR-10.1).
 *
 * <p>Thin wrapper over the MP REST API for two operations:</p>
 * <ol>
 *   <li><b>Create checkout preference</b> (Checkout Pro redirect flow).
 *       Returns an {@code init_point} URL the FE opens in a new tab.</li>
 *   <li><b>Get payment by id</b> — for the webhook handler to fetch
 *       the canonical status after a notification arrives (never
 *       trust the webhook payload alone).</li>
 * </ol>
 *
 * <h3>Why a thin wrapper, not the MP SDK</h3>
 * The official MP Java SDK drags in a transitive Gson + okhttp and
 * is barely maintained. {@code RestClient} (Spring 6) plus a small
 * {@code ObjectMapper} call is 50 lines we control.
 *
 * <h3>Sandbox mode (default)</h3>
 * When {@code app.payments.mercadopago.mode=sandbox} the access
 * token MUST be a sandbox token (starts with {@code TEST-}). In dev
 * we set a placeholder; calling real endpoints will return 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoClient {

    private final MercadoPagoProperties props;
    private final ObjectMapper objectMapper;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getAccessToken())
                .defaultHeader("x-meli-session-id", "edushift")
                .build();
    }

    /**
     * Create a Checkout Pro preference. Returns the {@code init_point}
     * (the URL the user should be redirected to).
     */
    public String createPreference(String invoicePublicUuid,
                                   String title,
                                   long amountCents,
                                   String currency,
                                   String guardianEmail) {
        BigDecimal amount = BigDecimal.valueOf(amountCents, 2);
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of(
                        "id", invoicePublicUuid,
                        "title", title,
                        "quantity", 1,
                        "unit_price", amount,
                        "currency_id", currency
                )),
                "payer", Map.of("email", guardianEmail == null ? "anon@edushift.local" : guardianEmail),
                "back_urls", Map.of(
                        "success", props.getSuccessUrl(),
                        "failure", props.getFailureUrl(),
                        "pending", props.getFailureUrl()
                ),
                "auto_return", "approved",
                "external_reference", invoicePublicUuid,
                "notification_url", props.getWebhookUrl(),
                "statement_descriptor", "EDUSHIFT"
        );
        try {
            String response = restClient().post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (response == null) {
                throw new PaymentFailedException("MP returned empty body for createPreference");
            }
            JsonNode node = objectMapper.readTree(response);
            String initPoint = props.isSandbox()
                    ? node.path("sandbox_init_point").asText(null)
                    : node.path("init_point").asText(null);
            if (initPoint == null || initPoint.isBlank()) {
                throw new PaymentFailedException("MP response missing init_point: " + response);
            }
            log.info("[MP] preference created for invoice={} amount={} {} initPoint={}",
                    invoicePublicUuid, amount, currency, initPoint);
            return initPoint;
        } catch (RestClientException e) {
            log.error("[MP] createPreference failed: {}", e.getMessage());
            throw new PaymentFailedException("MP createPreference failed: " + e.getMessage());
        } catch (Exception e) {
            throw new PaymentFailedException("MP createPreference parse error: " + e.getMessage());
        }
    }

    /**
     * Fetch a payment by id (canonical status from MP, not the
     * webhook payload). Returns the raw JSON.
     */
    public JsonNode getPayment(String externalId) {
        try {
            String body = restClient().get()
                    .uri("/v1/payments/{id}", externalId)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientException e) {
            throw new PaymentFailedException("MP getPayment failed: " + e.getMessage());
        } catch (Exception e) {
            throw new PaymentFailedException("MP getPayment parse error: " + e.getMessage());
        }
    }
}
