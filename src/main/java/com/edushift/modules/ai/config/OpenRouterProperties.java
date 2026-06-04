package com.edushift.modules.ai.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenRouter (LLM gateway) settings for the AI module.
 * Bound to {@code app.integrations.openrouter.*}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.integrations.openrouter")
public class OpenRouterProperties {

	private boolean enabled = false;

	/** API key. Provide via OPENROUTER_API_KEY (never hardcoded). */
	private String apiKey;

	private String baseUrl = "https://openrouter.ai/api/v1";

	/** Default model identifier (e.g. openai/gpt-4o-mini). */
	private String defaultModel;

	/** Optional HTTP-Referer header recommended by OpenRouter. */
	private String referer;

	/** Optional X-Title header recommended by OpenRouter. */
	private String title = "EduShift";

	private Duration timeout = Duration.ofSeconds(30);

	private int maxRetries = 2;

}
