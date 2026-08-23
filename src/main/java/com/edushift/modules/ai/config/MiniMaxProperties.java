package com.edushift.modules.ai.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MiniMax M3 API Platform settings for the AI module (BE-7c.1.1).
 *
 * <p>MiniMax exposes an OpenAI-compatible {@code /chat/completions}
 * endpoint (their "API Platform" product), so the wire shape is the
 * standard OpenAI chat-completion dialect — only the base URL, auth
 * scheme, and the master {@code enabled} flag are specific to MiniMax.</p>
 *
 * <h3>Activating</h3>
 * <pre>
 * app.llm.minimax.enabled=true
 * app.llm.minimax.api-key=...
 * app.llm.minimax.base-url=https://api.minimax.chat/v1   (default)
 * app.llm.minimax.default-model=MiniMax-M3
 * </pre>
 *
 * <p>If {@code enabled=false} (the default), the {@code MockLlmClient}
 * is used as the dev/test fallback. Future migrations to Kimi or GLM
 * (also OpenAI-compatible) will land behind their own
 * {@code @ConditionalOnProperty} flag and a parallel
 * {@code *Properties} class — same pattern as MiniMax.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.llm.minimax")
public class MiniMaxProperties {

    private boolean enabled = false;

    /** API key. Provide via MINIMAX_API_KEY (never hardcoded). */
    private String apiKey;

    /**
     * OpenAI-compatible chat completions base URL (oficial, según
     * docs https://platform.minimax.io/docs/guides/quickstart-preparation.md).
     * Anthropic-compatible alternativo: {@code https://api.minimax.io/anthropic}.
     */
    private String baseUrl = "https://api.minimax.io/v1";

    /** Default model identifier (per docs oficiales: {@code MiniMax-M3},
     *  otros válidos: MiniMax-M2.7, MiniMax-M2.7-highspeed, MiniMax-M2.5, etc). */
    private String defaultModel = "MiniMax-M3";

    private Duration timeout = Duration.ofSeconds(30);

    private int maxRetries = 2;
}
