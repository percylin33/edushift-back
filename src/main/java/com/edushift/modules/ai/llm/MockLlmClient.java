package com.edushift.modules.ai.llm;

import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op {@link LlmClient} used when no real LLM provider is enabled
 * (typical dev/test setup without an API key).
 *
 * <p>Returns a deterministic, hardcoded JSON blob that satisfies the
 * {@code LmsAiService}'s parser. This lets the AI module boot and serve
 * requests (so the FE panel is usable in dev) without a real LLM key.
 * The shape matches what a real OpenRouter call would return, so the
 * downstream parsing path is exercised end-to-end.</p>
 *
 * <p>Wiring lives in {@code com.edushift.modules.ai.config.LlmAutoConfiguration},
 * which registers this class as a {@code @Bean} via
 * {@code @ConditionalOnMissingBean(LlmClient.class)} — the bean factory
 * is evaluated AFTER the regular component scan, so any enabled real
 * provider ({@code OpenRouterLlmClient}, {@code MiniMaxLlmClient})
 * wins deterministically. This class is intentionally NOT a
 * {@code @Component}.</p>
 *
 * <h3>Test/dev behaviour</h3>
 * <ul>
 *   <li>Returns a 1-suggestion JSON stub if the user prompt contains
 *       the word {@code "fracciones"} (Spanish) — gives test/dev a
 *       non-trivial payload.</li>
 *   <li>Returns a 2-suggestion stub for any other topic.</li>
 *   <li>Sleeps a tiny amount of time to mimic network latency so the
 *       FE's loading state is observable.</li>
 *   <li>Reports fake token counts ({@code 42 / 28}).</li>
 * </ul>
 */
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    private static final String SUGGEST_MATH_JSON = """
            {
              "questions": [
                {
                  "prompt": "¿Cuánto es 1/2 + 1/4?",
                  "type": "MC",
                  "points": 5,
                  "options": [
                    { "label": "3/4",  "isCorrect": true,  "explanation": "1/2 = 2/4; 2/4 + 1/4 = 3/4." },
                    { "label": "1/3",  "isCorrect": false, "explanation": null },
                    { "label": "2/6",  "isCorrect": false, "explanation": null },
                    { "label": "1/2",  "isCorrect": false, "explanation": null }
                  ],
                  "rationale": "Suma de fracciones con denominador común. Nivel 5to primaria."
                }
              ]
            }
            """;

    private static final String SUGGEST_DEFAULT_JSON = """
            {
              "questions": [
                {
                  "prompt": "¿Cuál es la capital de Francia?",
                  "type": "MC",
                  "points": 5,
                  "options": [
                    { "label": "París",   "isCorrect": true,  "explanation": "Capital desde 987 d.C." },
                    { "label": "Londres", "isCorrect": false, "explanation": "Capital del Reino Unido." },
                    { "label": "Berlín",  "isCorrect": false, "explanation": "Capital de Alemania." }
                  ],
                  "rationale": "Pregunta clásica de geografía europea. Nivel básico."
                },
                {
                  "prompt": "Verdadero o falso: el agua hierve a 50°C al nivel del mar.",
                  "type": "TF",
                  "points": 3,
                  "options": [
                    { "label": "Verdadero", "isCorrect": false, "explanation": "El agua hierve a 100°C a 1 atm." },
                    { "label": "Falso",     "isCorrect": true,  "explanation": "Punto de ebullición del agua = 100°C a 1 atm." }
                  ],
                  "rationale": "Concepto físico elemental, formato rápido."
                }
              ]
            }
            """;

    @Override
    public String providerId() {
        return "mock";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        log.info("[MOCK LLM] prompt model={} systemLen={} userLen={} topic={}",
                request.model(),
                request.systemPrompt() == null ? 0 : request.systemPrompt().length(),
                request.userPrompt() == null ? 0 : request.userPrompt().length(),
                extractTopic(request.userPrompt()));
        // Tiny artificial latency so the FE's loading state is observable.
        sleepQuietly(120L + ThreadLocalRandom.current().nextLong(80));
        String text = request.userPrompt() != null && request.userPrompt().toLowerCase().contains("fracciones")
                ? SUGGEST_MATH_JSON
                : SUGGEST_DEFAULT_JSON;
        return new LlmResponse(text, request.model(), 42, 28, 0L);
    }

    private static String extractTopic(String userPrompt) {
        if (userPrompt == null) return "?";
        // The PromptBuilder always writes "TEMA: <topic>" near the end.
        int idx = userPrompt.lastIndexOf("TEMA:");
        return idx < 0 ? "?" : userPrompt.substring(idx).split("\\R", 2)[0];
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
