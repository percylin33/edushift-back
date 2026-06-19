package com.edushift.modules.ai.llm;

import com.edushift.modules.ai.config.MiniMaxProperties;
import com.edushift.shared.constants.LoggerNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MiniMax M3 API Platform-backed {@link LlmClient} (BE-7c.1.1).
 *
 * <p>MiniMax's "API Platform" product speaks the OpenAI-compatible
 * {@code /chat/completions} dialect, so this class is a thin
 * wrapper around {@link OpenAiHttpHelper} that only configures the
 * MiniMax-specific bits: provider id, auth scheme, base URL, and
 * the default model. Wire shape and error codes are the same as
 * OpenRouter's (the LLM contract is provider-agnostic).</p>
 *
 * <h3>Why a separate class (vs. parameterising OpenRouterLlmClient)</h3>
 * <p>MiniMax's auth header is identical ({@code Bearer <key>}) and
 * the wire shape is identical, but the operational concerns differ:
 * MiniMax has its own rate-limit semantics and may require
 * MiniMax-specific {@code extra} params (e.g.
 * {@code response_format={"type":"json_object"}}). Keeping a
 * dedicated client lets us evolve each provider independently
 * without breaking the other.</p>
 *
 * <h3>Activation</h3>
 * <p>Active when {@code app.llm.minimax.enabled=true} AND the API
 * key is set. Mutually exclusive with {@link OpenRouterLlmClient} by
 * convention — if both are enabled, Spring will fail to start with
 * an "ambiguous bean" error, which is the correct behavior.</p>
 *
 * <h3>Retry policy</h3>
 * Mirrors OpenRouter's: up to {@code MiniMaxProperties.maxRetries}
 * retries on {@code TIMEOUT}/{@code NETWORK}/{@code 5xx}/{@code 429},
 * exponential backoff capped at 4s.
 */
@Component
@ConditionalOnProperty(prefix = "app.llm.minimax", name = "enabled", havingValue = "true")
public class MiniMaxLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    private final MiniMaxProperties props;
    private OpenAiHttpHelper helper;

    /**
     * Production constructor wired by Spring. Builds its own
     * {@link HttpClient} inside the helper.
     */
    public MiniMaxLlmClient(MiniMaxProperties props, ObjectMapper objectMapper) {
        this.props = props;
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.llm.minimax.enabled=true but app.llm.minimax.api-key is empty");
        }
        this.helper = new OpenAiHttpHelper(
                objectMapper,
                props.getBaseUrl(),
                "minimax",
                "Bearer " + props.getApiKey(),
                Map.of(),
                props.getTimeout(),
                HttpClient.newBuilder().connectTimeout(props.getTimeout()).build()
        );
    }

    /**
     * Test-only seam: replace the {@link OpenAiHttpHelper} with one
     * that has a stubbed {@link HttpClient}. Used by
     * {@code MiniMaxLlmClientTest} to inject canned responses without
     * a real network. Package-private so production code can't reach it.
     */
    void setHelperForTesting(OpenAiHttpHelper helper) {
        this.helper = helper;
    }

    @Override
    public String providerId() {
        return "minimax";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.nanoTime();
        int attempts = 0;
        int maxAttempts = Math.max(1, props.getMaxRetries() + 1);
        long backoffMs = 250L;

        while (true) {
            attempts++;
            try {
                LlmResponse response = helper.executeOnce(request);
                long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                return new LlmResponse(
                        response.text(),
                        response.model(),
                        response.tokensIn(),
                        response.tokensOut(),
                        latencyMs
                );
            } catch (LlmException e) {
                if (!isRetryable(e.getCode()) || attempts >= maxAttempts) {
                    log.warn("minimax LLM call failed (code={}, attempts={}/{}): {}",
                            e.getCode(), attempts, maxAttempts, e.getMessage());
                    throw e;
                }
                log.info("minimax LLM call retryable error (code={}, attempt {}/{}); sleeping {}ms",
                        e.getCode(), attempts, maxAttempts, backoffMs);
                sleepQuietly(backoffMs);
                backoffMs = Math.min(backoffMs * 2L, 4000L);
            }
        }
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
