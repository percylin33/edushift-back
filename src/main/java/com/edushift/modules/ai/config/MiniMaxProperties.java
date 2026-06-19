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
 * same as OpenRouter — only the base URL, auth scheme, and the
 * master {@code enabled} flag differ.</p>
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
 * is used. The setting is independent of OpenRouter: enabling both
 * throws on startup (the two beans are mutually exclusive by
 * convention — never run two LLMs in the same request stream).</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.llm.minimax")
public class MiniMaxProperties {

    private boolean enabled = false;

    /** API key. Provide via MINIMAX_API_KEY (never hardcoded). */
    private String apiKey;

    private String baseUrl = "https://api.minimax.chat/v1";

    /** Default model identifier (e.g. {@code MiniMax-M3}). */
    private String defaultModel = "MiniMax-M3";

    private Duration timeout = Duration.ofSeconds(30);

    private int maxRetries = 2;
}
