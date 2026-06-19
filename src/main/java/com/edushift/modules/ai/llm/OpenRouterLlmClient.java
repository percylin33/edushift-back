package com.edushift.modules.ai.llm;

import com.edushift.modules.ai.config.OpenRouterProperties;
import com.edushift.shared.constants.LoggerNames;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenRouter-backed {@link LlmClient} (BE-7c.1, refactored in BE-7c.1.1).
 *
 * <p>OpenRouter exposes the OpenAI-compatible {@code /chat/completions}
 * endpoint, so the heavy lifting is delegated to
 * {@link OpenAiHttpHelper}. This class is now a thin wrapper that
 * knows the OpenRouter-specific bits: provider id, headers
 * ({@code HTTP-Referer}, {@code X-Title}), and the retry policy.</p>
 *
 * <p>Active when {@code app.llm.openrouter.enabled=true} (defaults to
 * the existing {@code app.integrations.openrouter.enabled} flag for
 * backward compatibility). See {@link MockLlmClient} for the
 * fallback when no real provider is enabled.</p>
 *
 * <h3>Retry policy</h3>
 * <ul>
 *   <li>Up to {@code OpenRouterProperties.maxRetries} retries on
 *       {@code TIMEOUT} / {@code NETWORK} / {@code 5xx} / {@code 429}.</li>
 *   <li>Exponential backoff: 250ms, 500ms, 1s, 2s, 4s...</li>
 *   <li>No retry on {@code 4xx} (other than 429) — those are permanent
 *       and point at a bug in the prompt or the configuration.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "app.llm.openrouter", name = "enabled", havingValue = "true")
public class OpenRouterLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    private final OpenRouterProperties props;
    private final OpenAiHttpHelper helper;

    public OpenRouterLlmClient(OpenRouterProperties props) {
        this.props = props;
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            // The bean should not even be loaded (ConditionalOnProperty ensures
            // the user opted in), but we double-check at construction time so
            // the first request does not fail with a confusing NullPointerException.
            throw new IllegalStateException(
                    "app.llm.provider=openrouter but app.integrations.openrouter.api-key is empty");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (props.getReferer() != null && !props.getReferer().isBlank()) {
            headers.put("HTTP-Referer", props.getReferer());
        }
        if (props.getTitle() != null && !props.getTitle().isBlank()) {
            headers.put("X-Title", props.getTitle());
        }
        this.helper = new OpenAiHttpHelper(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                props.getBaseUrl(),
                "openrouter",
                "Bearer " + props.getApiKey(),
                headers,
                props.getTimeout()
        );
    }

    @Override
    public String providerId() {
        return "openrouter";
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
                    log.warn("LLM call failed (code={}, attempts={}/{}): {}",
                            e.getCode(), attempts, maxAttempts, e.getMessage());
                    throw e;
                }
                long sleep = backoffMs;
                log.info("LLM call retryable error (code={}, attempt {}/{}); sleeping {}ms",
                        e.getCode(), attempts, maxAttempts, sleep);
                sleepQuietly(sleep);
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
