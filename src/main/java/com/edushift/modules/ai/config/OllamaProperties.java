package com.edushift.modules.ai.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local Ollama settings. Ollama exposes an OpenAI-compatible
 * {@code /v1/chat/completions} endpoint and does not require an API key.
 *
 * <pre>
 * app.llm.ollama.enabled=true
 * app.llm.ollama.base-url=http://localhost:11434/v1
 * app.llm.ollama.default-model=gemma4:e2b
 * </pre>
 *
 * <p>When this flag is true, {@code OllamaLlmClient} is the active
 * {@code LlmClient} and the MiniMax bean stays off.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.llm.ollama")
public class OllamaProperties {

    private boolean enabled = false;

    /** OpenAI-compatible base, including the {@code /v1} prefix. */
    private String baseUrl = "http://localhost:11434/v1";

    /** Tag as reported by {@code ollama list}, e.g. {@code gemma4:e2b}. */
    private String defaultModel = "gemma4:e2b";

    /** Local models are slower on first load than a hosted API. */
    private Duration timeout = Duration.ofSeconds(120);

    private int maxRetries = 1;
}
