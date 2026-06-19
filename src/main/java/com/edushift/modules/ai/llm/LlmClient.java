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
     * Stream a chat-style completion token-by-token (Sprint 8 / BE-8.3).
     *
     * <p>Emits each chunk via the {@link StreamObserver}; the observer is
     * responsible for whatever transport the caller wants (SSE, WebSocket,
     * reactive subscription, ...). The impl is expected to be
     * <b>cancellation-aware</b>: if the observer throws (or returns false
     * from {@code onToken}), the underlying HTTP request must be closed.
     *
     * <p>Default implementation (BE-7c.1 backward compat): runs
     * {@link #complete(LlmRequest)} and emits the resulting text in
     * pseudo-token chunks of ~20 chars. Real providers (OpenRouter,
     * MiniMax) override this with native SSE / chunked responses.
     *
     * <p>The returned {@link StreamResult} carries the final token usage
     * and latency, same as {@link LlmResponse}.
     *
     * @param request fully-built prompt (same contract as {@code complete}).
     * @param observer sink for streaming chunks. Must not be null.
     * @return terminal result. Never null.
     */
    default StreamResult stream(LlmRequest request, StreamObserver observer) {
        long start = System.currentTimeMillis();
        LlmResponse resp = complete(request);
        String text = resp.text() == null ? "" : resp.text();
        // Emit in ~20-char chunks with a tiny per-token delay so the
        // SSE experience in the FE is realistic in dev (mock provider).
        int chunkSize = 20;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(text.length(), i + chunkSize);
            String chunk = text.substring(i, end);
            boolean keepGoing = observer.onToken(chunk);
            if (!keepGoing) {
                return new StreamResult(resp.model(), resp.tokensIn(), resp.tokensOut(),
                        System.currentTimeMillis() - start, true);
            }
            try { Thread.sleep(15); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new StreamResult(resp.model(), resp.tokensIn(), resp.tokensOut(),
                        System.currentTimeMillis() - start, true);
            }
        }
        observer.onComplete();
        return new StreamResult(resp.model(), resp.tokensIn(), resp.tokensOut(),
                System.currentTimeMillis() - start, false);
    }

    /**
     * Terminal result of a streaming call. Equivalent to {@link LlmResponse}
     * but exposes whether the stream was cancelled before completion.
     */
    record StreamResult(
            String model,
            Integer tokensIn,
            Integer tokensOut,
            long latencyMs,
            boolean cancelled
    ) {}

    /**
     * Observer sink for {@link #stream(LlmRequest, StreamObserver)}.
     *
     * <p>Implementations decide what to do with each chunk (write to
     * an {@code SseEmitter}, push to a WebSocket, accumulate in memory,
     * ...). Returning {@code false} from {@link #onToken} signals the
     * provider to stop emitting; returning {@code true} (or void) means
     * "keep going".
     */
    interface StreamObserver {
        /**
         * Called for each emitted chunk. Return {@code false} to request
         * cancellation of the upstream stream.
         */
        default boolean onToken(String chunk) { return true; }
        /** Called once after the last chunk (or skipped if cancelled). */
        default void onComplete() {}
        /** Called if the provider hits an unrecoverable error mid-stream. */
        default void onError(Throwable error) {}
    }

    /**
     * Default delegating observer: forwards every callback to the wrapped
     * observer. Callers subclass to add side-effects (e.g. accumulating
     * the buffer for persistence, or propagating to an SSE emitter).
     */
    class WrappingObserver implements StreamObserver {
        private final StreamObserver delegate;
        public WrappingObserver(StreamObserver delegate) { this.delegate = delegate; }
        @Override public boolean onToken(String chunk) {
            return delegate == null || delegate.onToken(chunk);
        }
        @Override public void onComplete() { if (delegate != null) delegate.onComplete(); }
        @Override public void onError(Throwable error) { if (delegate != null) delegate.onError(error); }
    }

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
            Map<String, Object> extra,
            List<HistoryItem> history
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

        /** Compact constructor used by BE-7c.1 callers (no history). */
        public LlmRequest(String model, String systemPrompt, String userPrompt,
                          Double temperature, Integer maxTokens,
                          List<String> stopSequences, Map<String, Object> extra) {
            this(model, systemPrompt, userPrompt, temperature, maxTokens,
                    stopSequences, extra, List.of());
        }
    }

    /**
     * One message in a chat history (Sprint 8 / BE-8.3). The chat
     * service builds a list of these and passes it via
     * {@link LlmRequest#history()}. Real providers (OpenRouter,
     * MiniMax) translate this into the {@code messages} array of the
     * chat-completion request.
     *
     * @param role    {@code "user"} or {@code "assistant"}.
     * @param content the message text.
     */
    record HistoryItem(String role, String content) {}

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
