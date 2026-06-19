package com.edushift.modules.ai.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic LLM gateway (BE-7c.1, ai-rules.mdc §AI ARCHITECTURE).
 *
 * <p>Wraps any text-completion model behind a stable contract so the
 * {@code LmsAiService} (and the rest of the AI module) does not depend
 * on a specific provider (OpenAI, OpenRouter, Anthropic, Ollama, ...).
 * The implementation is selected at runtime by the
 * {@code llm} configuration profile; see
 * {@link com.edushift.modules.ai.llm.OpenRouterLlmClient} and
 * {@link com.edushift.modules.ai.llm.MockLlmClient} for the two
 * bundled impls.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li><b>Stateless</b>: instances are reused across requests. No
 *       mutable state, no per-request setup.</li>
 *   <li><b>Sync</b>: returns a {@link LlmResponse} with the full text
 *       reply (no streaming in BE-7c.1; streaming lands in BE-7c.2 via
 *       SSE per ai-rules.mdc §STREAMING RULES).</li>
 *   <li><b>Retry-aware</b>: the impl performs retries with backoff on
 *       transient errors (5xx, timeouts). The caller does NOT need to
 *       retry. {@code maxRetries} is configured at the impl level
 *       (see {@code OpenRouterProperties.maxRetries}).</li>
 *   <li><b>Throws on terminal failures</b>: after retries are exhausted
 *       or on a permanent error (4xx, parse error), the impl throws an
 *       {@link LlmException} with a stable {@code code} that the
 *       {@code AiController} maps to an HTTP error.</li>
 * </ul>
 *
 * @see OpenRouterLlmClient
 * @see MockLlmClient
 */
public interface LlmClient {

    /**
     * Execute a chat-style completion and return the model's text reply.
     *
     * @param request fully-built prompt and configuration. See
     *                {@link LlmRequest}.
     * @return the model's response (text + token usage). Never null.
     * @throws LlmException on any terminal failure (after retries). The
     *                      exception carries a stable {@link LlmException#code()}.
     */
    LlmResponse complete(LlmRequest request);

    /**
     * Human-readable provider id (e.g. {@code "openrouter"}, {@code "mock"}).
     * Used in the {@code ai_generations.model_used} audit row and in the
     * {@code X-Provider} response header.
     */
    String providerId();

    /**
     * Per-request LLM configuration.
     *
     * @param model          OpenRouter model id, e.g. {@code openai/gpt-4o-mini}.
     *                       Required. Picked by the caller from
     *                       {@code tenant_ai_settings.default_model} or
     *                       the app-level default.
     * @param systemPrompt   System-role message. Used to anchor the model's
     *                       behaviour (the EduShift "persona"). Required.
     * @param userPrompt     User-role message (the actual task). Required.
     * @param temperature    Sampling temperature. {@code null} = use impl default
     *                       (usually 0.7). For structured-output tasks (like
     *                       question suggestion) we set it to 0.2 in the
     *                       caller.
     * @param maxTokens      Hard cap on output tokens. {@code null} = use
     *                       impl default (e.g. 2048).
     * @param stopSequences  Optional list of stop sequences.
     * @param extra          Provider-specific passthrough (e.g.
     *                       {@code response_format}, {@code top_p}). Keys
     *                       are documented per-provider.
     */
    record LlmRequest(
            String model,
            String systemPrompt,
            String userPrompt,
            Double temperature,
            Integer maxTokens,
            List<String> stopSequences,
            Map<String, Object> extra
    ) {
        public LlmRequest {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            if (systemPrompt == null || systemPrompt.isBlank()) {
                throw new IllegalArgumentException("systemPrompt is required");
            }
            if (userPrompt == null || userPrompt.isBlank()) {
                throw new IllegalArgumentException("userPrompt is required");
            }
        }
    }

    /**
     * The LLM's reply, plus the telemetry we want to persist.
     *
     * @param text           Raw model reply (the text the model wrote).
     *                       Always present, never null. May include
     *                       reasoning-think blocks depending on the model;
     *                       the caller is responsible for stripping/parsing.
     * @param model          Model id that produced the reply. May differ
     *                       from the request if the provider auto-routed.
     * @param tokensIn       Input tokens consumed. May be {@code null} if
     *                       the provider does not return usage.
     * @param tokensOut      Output tokens produced. May be {@code null}.
     * @param latencyMs      Wall-clock time spent waiting for the LLM.
     *                       Computed by the impl.
     */
    record LlmResponse(
            String text,
            String model,
            Integer tokensIn,
            Integer tokensOut,
            long latencyMs
    ) {
        public LlmResponse {
            if (text == null) {
                throw new IllegalArgumentException("text is required");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
        }
    }
}
