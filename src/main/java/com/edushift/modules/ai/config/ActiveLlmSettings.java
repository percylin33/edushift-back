package com.edushift.modules.ai.config;

import org.springframework.stereotype.Component;

/**
 * Picks the model id the active provider can actually serve.
 *
 * <p>Tenant rows still store {@code MiniMax-M3} from the V91 migration.
 * While Ollama is the provider, those ids are replaced with the local
 * tag ({@code gemma4:e2b} by default) so chat and generators do not
 * send a hosted model name to localhost.</p>
 */
@Component
public class ActiveLlmSettings {

    private final MiniMaxProperties minimax;
    private final OllamaProperties ollama;

    public ActiveLlmSettings(MiniMaxProperties minimax, OllamaProperties ollama) {
        this.minimax = minimax;
        this.ollama = ollama;
    }

    public boolean ollamaEnabled() {
        return ollama.isEnabled();
    }

    public String defaultModel() {
        if (ollama.isEnabled()
                && ollama.getDefaultModel() != null
                && !ollama.getDefaultModel().isBlank()) {
            return ollama.getDefaultModel().trim();
        }
        if (minimax.getDefaultModel() != null && !minimax.getDefaultModel().isBlank()) {
            return minimax.getDefaultModel().trim();
        }
        return "MiniMax-M3";
    }

    /**
     * Tenant override when it is valid for the active provider; otherwise
     * the provider default.
     */
    public String resolve(String configured) {
        if (ollama.isEnabled()) {
            if (configured == null || configured.isBlank() || isForeignToOllama(configured)) {
                return defaultModel();
            }
            return configured.trim();
        }
        if (configured == null || configured.isBlank() || isLegacyHostedId(configured)) {
            return defaultModel();
        }
        return configured.trim();
    }

    private static boolean isLegacyHostedId(String model) {
        String trimmed = model.trim();
        String lower = trimmed.toLowerCase();
        return trimmed.contains("/")
                || lower.startsWith("gpt-")
                || lower.startsWith("claude-");
    }

    /**
     * Hosted ids that Ollama will reject: MiniMax names, OpenRouter
     * {@code provider/model}, and the legacy gpt/claude prefixes.
     */
    public static boolean isForeignToOllama(String model) {
        String trimmed = model.trim();
        String lower = trimmed.toLowerCase();
        return lower.startsWith("minimax")
                || trimmed.contains("/")
                || lower.startsWith("gpt-")
                || lower.startsWith("claude-");
    }
}
