package com.edushift.modules.ai.config;

import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.MockLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring of the {@link LlmClient} provider chain (Sprint 7c).
 *
 * <p>A real provider registers itself as a {@code @Component} gated by
 * {@code @ConditionalOnProperty}: {@code OllamaLlmClient} when
 * {@code app.llm.ollama.enabled=true}, otherwise {@code MiniMaxLlmClient}
 * when MiniMax is enabled and has an API key. When either is present,
 * {@link #mockLlmClient()} is skipped.</p>
 *
 * <p>If no real provider is enabled (typical for dev/test without an API
 * key), the {@link MockLlmClient} fallback is registered via a {@code @Bean}
 * factory with {@code @ConditionalOnMissingBean(LlmClient.class)}.
 *
 * <h3>Why not {@code @ConditionalOnMissingBean} directly on {@code MockLlmClient}?</h3>
 * <p>{@code @ConditionalOnMissingBean} placed on a plain {@code @Component}
 * is evaluated during component scan, and its result depends on scan
 * order — Spring Boot's own docs strongly recommend restricting that
 * annotation to auto-configuration / {@code @Bean} factories so the
 * condition is evaluated AFTER the regular component scan has finished.
 * Moving the registration here makes the fallback deterministic.</p>
 */
@Configuration
public class LlmAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LlmAutoConfiguration.class);

	/**
	 * Registers {@link MockLlmClient} only when no other {@link LlmClient}
	 * bean exists in the context. Logs a WARN so the operator knows AI
	 * responses are stubs instead of a real LLM call.
	 */
	@Bean
	@ConditionalOnMissingBean(LlmClient.class)
	public LlmClient mockLlmClient() {
		log.warn("[llm-config] No real LLM provider registered "
				+ "(OllamaLlmClient and MiniMaxLlmClient absent — ollama disabled, "
				+ "or MiniMax disabled / api-key empty). "
				+ "Falling back to MockLlmClient — AI responses are deterministic stubs.");
		return new MockLlmClient();
	}
}
