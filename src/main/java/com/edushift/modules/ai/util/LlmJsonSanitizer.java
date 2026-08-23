package com.edushift.modules.ai.util;

/**
 * Normalises raw LLM text into a JSON object string before Jackson parsing.
 *
 * <p>MiniMax-M3 and other reasoning models may prepend
 * {@code <think>...</think>} blocks or wrap the payload
 * in markdown fences despite {@code response_format=json_object}.</p>
 */
public final class LlmJsonSanitizer {

    private LlmJsonSanitizer() {
    }

    /**
     * @param text raw {@code choices[0].message.content} from the provider
     * @return a string suitable for {@code objectMapper.readValue(...)} /
     *         {@code readTree(...)}
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("(?is)<think>.*?</think>", "").trim();
        if (cleaned.startsWith("```")) {
            int firstNl = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                cleaned = cleaned.substring(firstNl + 1, lastFence).trim();
            }
        }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }
        return cleaned.trim();
    }
}
