package com.edushift.modules.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Shared transport layer for {@link LlmClient} implementations that speak
 * the OpenAI-compatible {@code /chat/completions} protocol
 * (BE-7c.1.1).
 *
 * <p>Both OpenRouter and the MiniMax M3 API Platform expose this
 * dialect, so we centralise the request/response shape here and let
 * the two clients (which differ only on auth, base URL, and the
 * {@code X-Provider} header) plug in their specifics via
 * {@link #authHeader()}, {@link #extraHeaders()}, and
 * {@link #providerId()}.</p>
 *
 * <p>This is a pure helper — it is NOT itself an {@link LlmClient} bean
 * and is not visible to the rest of the app. The retry loop
 * (backoff, max-attempts) is still owned by each concrete client,
 * because the timing characteristics differ per provider
 * (e.g. OpenRouter is more aggressive with {@code Retry-After},
 * MiniMax has its own rate-limit semantics).</p>
 *
 * <h3>Wire shape</h3>
 * <pre>
 * POST {baseUrl}/chat/completions
 * Headers: Authorization, Content-Type, [extraHeaders]
 * Body:    { model, messages, temperature, max_tokens, stop, [extra] }
 * Reply:   { id, model, choices: [{message: {role, content}}], usage }
 * </pre>
 */
final class OpenAiHttpHelper {

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String providerId;
    private final String authHeader;
    private final Map<String, String> extraHeaders;
    private final Duration timeout;

    OpenAiHttpHelper(
            ObjectMapper objectMapper,
            String baseUrl,
            String providerId,
            String authHeader,
            Map<String, String> extraHeaders,
            Duration timeout) {
        this(objectMapper, baseUrl, providerId, authHeader, extraHeaders, timeout,
                HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    /**
     * Test-friendly constructor that accepts an externally-built
     * {@link HttpClient} (e.g. one whose body handler is a stub). Not
     * intended for production wiring.
     */
    OpenAiHttpHelper(
            ObjectMapper objectMapper,
            String baseUrl,
            String providerId,
            String authHeader,
            Map<String, String> extraHeaders,
            Duration timeout,
            HttpClient http) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.providerId = providerId;
        this.authHeader = authHeader;
        this.extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
        this.timeout = timeout;
        this.http = http;
    }

    /**
     * Execute a single chat-completion attempt (no retry — the caller's
     * retry loop wraps this).
     *
     * @throws LlmException on any terminal failure (HTTP non-2xx, timeout,
     *                      network error, malformed body).
     */
    LlmClient.LlmResponse executeOnce(LlmClient.LlmRequest request) {
        try {
            ChatCompletionsRequest body = new ChatCompletionsRequest(
                    request.model(),
                    List.of(
                            new ChatMessage("system", request.systemPrompt()),
                            new ChatMessage("user", request.userPrompt())
                    ),
                    request.temperature(),
                    request.maxTokens(),
                    request.stopSequences(),
                    request.extra()
            );
            byte[] payload = objectMapper.writeValueAsBytes(body);
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(baseUrl) + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(BodyPublishers.ofByteArray(payload));
            for (Map.Entry<String, String> h : extraHeaders.entrySet()) {
                b.header(h.getKey(), h.getValue());
            }
            HttpResponse<String> response = http.send(b.build(), BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return parseSuccess(response.body(), request.model());
            }
            throw mapHttpError(status, response.body());
        } catch (LlmException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException(LlmException.TIMEOUT,
                    providerId + " request exceeded " + timeout, e);
        } catch (java.io.IOException e) {
            throw new LlmException(LlmException.NETWORK,
                    "Could not reach " + providerId + " at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.NETWORK,
                    "Interrupted while calling " + providerId, e);
        } catch (Exception e) {
            throw new LlmException(LlmException.UPSTREAM,
                    "Unexpected error talking to " + providerId + ": " + e.getMessage(), e);
        }
    }

    private LlmClient.LlmResponse parseSuccess(String json, String fallbackModel) {
        try {
            ChatCompletionsResponse resp = objectMapper.readValue(json, ChatCompletionsResponse.class);
            if (resp.choices == null || resp.choices.isEmpty()) {
                throw new LlmException(LlmException.EMPTY_RESPONSE,
                        providerId + " returned 200 with no choices");
            }
            String text = resp.choices.get(0).message() != null
                    ? resp.choices.get(0).message().content() : null;
            if (text == null || text.isBlank()) {
                throw new LlmException(LlmException.EMPTY_RESPONSE,
                        providerId + " returned an empty message content");
            }
            Integer tokensIn = null;
            Integer tokensOut = null;
            if (resp.usage != null) {
                tokensIn = resp.usage.promptTokens();
                tokensOut = resp.usage.completionTokens();
            }
            String model = resp.model() != null ? resp.model() : fallbackModel;
            return new LlmClient.LlmResponse(text, model, tokensIn, tokensOut, 0L);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new LlmException(LlmException.UPSTREAM,
                    "Could not parse " + providerId + " success response: " + e.getMessage(), e);
        }
    }

    private LlmException mapHttpError(int status, String body) {
        String snippet = body == null ? "" : body.length() > 300 ? body.substring(0, 300) + "..." : body;
        return switch (status) {
            case 401, 403 -> new LlmException(LlmException.AUTH,
                    providerId + " rejected our credentials (status " + status + "): " + snippet);
            case 402 -> new LlmException(LlmException.QUOTA,
                    providerId + " account is out of credits (402): " + snippet);
            case 429 -> new LlmException(LlmException.RATE_LIMITED,
                    providerId + " rate-limited us (429): " + snippet);
            case 400, 404, 422 -> new LlmException(LlmException.BAD_REQUEST,
                    providerId + " rejected the request (status " + status + "): " + snippet);
            default -> new LlmException(LlmException.UPSTREAM,
                    providerId + " upstream error (status " + status + "): " + snippet);
        };
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ---------------------------------------------------------------------
    // Wire-shape records (OpenAI-compatible). Kept package-private — they
    // are an implementation detail of the helper.
    // ---------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionsRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("stop") List<String> stop,
            Map<String, Object> extra
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionsResponse(
            String id,
            String model,
            List<ChatChoice> choices,
            Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChoice(Integer index, ChatMessage message, String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}
