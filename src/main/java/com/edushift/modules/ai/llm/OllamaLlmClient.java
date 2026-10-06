package com.edushift.modules.ai.llm;

import com.edushift.modules.ai.config.OllamaProperties;
import com.edushift.shared.constants.LoggerNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Ollama-backed {@link LlmClient}. Ollama's OpenAI-compatible endpoint
 * ({@code POST /v1/chat/completions}) speaks the same dialect as
 * {@link MiniMaxLlmClient}, without an API key.
 *
 * <p>Active only when {@code app.llm.ollama.enabled=true}. That flag
 * also suppresses {@link MiniMaxLlmClient} so Spring sees a single
 * {@link LlmClient} bean.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.llm.ollama", name = "enabled", havingValue = "true")
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    private final OllamaProperties props;
    private OpenAiHttpHelper helper;

    public OllamaLlmClient(OllamaProperties props, ObjectMapper objectMapper) {
        this.props = props;
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "app.llm.ollama.enabled=true pero app.llm.ollama.base-url está vacía.");
        }
        log.info("[llm-config] Initializing OllamaLlmClient (model={}, base={}, timeout={})",
                props.getDefaultModel(), props.getBaseUrl(), props.getTimeout());
        this.helper = new OpenAiHttpHelper(
                objectMapper,
                props.getBaseUrl(),
                "ollama",
                null,
                Map.of(),
                props.getTimeout(),
                HttpClient.newBuilder().connectTimeout(props.getTimeout()).build()
        );
    }

    void setHelperForTesting(OpenAiHttpHelper helper) {
        this.helper = helper;
    }

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.nanoTime();
        int attempts = 0;
        int maxAttempts = Math.max(1, props.getMaxRetries() + 1);
        long backoffMs = 250L;
        LlmRequest outbound = withoutMiniMaxExtras(request);

        while (true) {
            attempts++;
            try {
                LlmResponse response = helper.executeOnce(outbound);
                long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                String text = stripThink(response.text());
                if (text.isBlank()) {
                    throw new LlmException(LlmException.EMPTY_RESPONSE,
                            "ollama returned only a thinking block");
                }
                return new LlmResponse(
                        text,
                        response.model(),
                        response.tokensIn(),
                        response.tokensOut(),
                        latencyMs
                );
            } catch (LlmException e) {
                if (!isRetryable(e.getCode()) || attempts >= maxAttempts) {
                    log.warn("ollama LLM call failed (code={}, attempts={}/{}): {}",
                            e.getCode(), attempts, maxAttempts, e.getMessage());
                    throw e;
                }
                log.info("ollama LLM call retryable error (code={}, attempt {}/{}); sleeping {}ms",
                        e.getCode(), attempts, maxAttempts, backoffMs);
                sleepQuietly(backoffMs);
                backoffMs = Math.min(backoffMs * 2L, 4000L);
            }
        }
    }

    /**
     * MiniMax-only body fields. Ollama ignores unknown keys on most
     * builds, but {@code thinking} is not part of its OpenAI dialect.
     */
    private static LlmRequest withoutMiniMaxExtras(LlmRequest request) {
        if (request.extra() == null || !request.extra().containsKey("thinking")) {
            return request;
        }
        Map<String, Object> extra = new LinkedHashMap<>(request.extra());
        extra.remove("thinking");
        return new LlmRequest(
                request.model(),
                request.systemPrompt(),
                request.userPrompt(),
                request.temperature(),
                request.maxTokens(),
                request.stopSequences(),
                extra,
                request.history()
        );
    }

    static String stripThink(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?is)<think>.*?</think>", "").trim();
    }

    private static boolean isRetryable(String code) {
        return LlmException.TIMEOUT.equals(code)
                || LlmException.NETWORK.equals(code)
                || LlmException.RATE_LIMITED.equals(code)
                || LlmException.UPSTREAM.equals(code);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
