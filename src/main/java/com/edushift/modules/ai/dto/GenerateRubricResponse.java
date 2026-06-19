package com.edushift.modules.ai.dto;

import com.edushift.modules.ai.exception.AiParseException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Response payload for {@code POST /v1/ai/generate-rubric} (BE-8.2).
 *
 * <p>Mirrors the {@code CreateRubricRequest} shape 1:1 so the FE can
 * drop the response into a {@code POST /v1/academic/rubrics} call
 * verbatim (after renaming the rubric if desired). The shape is
 * enforced by {@link #validate(String)} after deserialisation.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * {
 *   "name": "Rúbrica de Ensayo Argumentativo",
 *   "description": "Evalúa ensayos argumentativos de secundaria (4 niveles MINEDU).",
 *   "criteria": [
 *     {
 *       "key": "thesis_clarity",
 *       "name": "Claridad de tesis",
 *       "description": "La tesis se enuncia de forma explícita y se sostiene…",
 *       "weight": 25.0,
 *       "descriptors": [
 *         { "level": "EN_INICIO",        "text": "La tesis es ambigua o ausente." },
 *         { "level": "EN_PROCESO",       "text": "..." },
 *         { "level": "LOGRO_ESPERADO",   "text": "..." },
 *         { "level": "LOGRO_DESTACADO",  "text": "..." }
 *       ]
 *     }
 *   ],
 *   "levels": [
 *     { "code": "EN_INICIO",       "name": "En inicio",       "order": 0 },
 *     { "code": "EN_PROCESO",      "name": "En proceso",      "order": 1 },
 *     { "code": "LOGRO_ESPERADO",  "name": "Logro esperado",  "order": 2 },
 *     { "code": "LOGRO_DESTACADO", "name": "Logro destacado", "order": 3 }
 *   ]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateRubricResponse(
        String name,
        String description,
        List<Criterion> criteria,
        List<Level> levels
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Criterion(
            String key,
            String name,
            String description,
            BigDecimal weight,
            List<Descriptor> descriptors
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Level(
            String code,
            String name,
            Integer order
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Descriptor(
            String level,
            String text
    ) {
    }

    /**
     * Validates the response shape against the {@code rubric-generator/v1}
     * contract (the one the system prompt instructs the model to emit).
     * Throws {@link AiParseException} on any structural problem.
     *
     * <p>Rules enforced (mirroring {@code RubricValidationService}):</p>
     * <ul>
     *   <li>name: required, 2..160 chars.</li>
     *   <li>criteria: 1..10 items, each with a unique snake_case key,
     *       weight in 0..100, weights sum to 100.0 (±0.01).</li>
     *   <li>levels: 2..4 items, unique codes, {@code order} 0..n-1.</li>
     *   <li>descriptors per criterion: ≤ levels.size(), each
     *       descriptor.level must match a defined level.</li>
     * </ul>
     */
    public void validate(String rawText) {
        String snippet = firstLine(rawText);
        if (name == null || name.isBlank() || name.length() < 2 || name.length() > 160) {
            int got = name == null ? 0 : name.length();
            throw new AiParseException(
                    "RubricGenerator output: 'name' must be 2..160 chars, got " + got + ". LLM text: " + snippet);
        }
        if (description != null && description.length() > 2000) {
            throw new AiParseException(
                    "RubricGenerator output: 'description' must be ≤2000 chars, got " + description.length()
                            + ". LLM text: " + snippet);
        }
        if (criteria == null || criteria.isEmpty() || criteria.size() > 10) {
            int got = criteria == null ? 0 : criteria.size();
            throw new AiParseException(
                    "RubricGenerator output: 'criteria' must have 1..10 items, got " + got + ". LLM text: " + snippet);
        }
        if (levels == null || levels.size() < 2 || levels.size() > 4) {
            int got = levels == null ? 0 : levels.size();
            throw new AiParseException(
                    "RubricGenerator output: 'levels' must have 2..4 items, got " + got + ". LLM text: " + snippet);
        }
        // Levels: unique codes, monotonic order.
        Set<String> levelCodes = new HashSet<>();
        for (int i = 0; i < levels.size(); i++) {
            Level l = levels.get(i);
            if (l == null || l.code() == null || l.code().isBlank()) {
                throw new AiParseException(
                        "RubricGenerator output: levels[" + i + "].code is required. LLM text: " + snippet);
            }
            if (l.name() == null || l.name().isBlank()) {
                throw new AiParseException(
                        "RubricGenerator output: levels[" + i + "].name is required. LLM text: " + snippet);
            }
            if (!levelCodes.add(l.code())) {
                throw new AiParseException(
                        "RubricGenerator output: duplicate level code '" + l.code() + "'. LLM text: " + snippet);
            }
        }
        // Criteria: unique keys, weights in range, descriptors valid.
        Set<String> seenKeys = new HashSet<>();
        double weightSum = 0.0;
        for (int i = 0; i < criteria.size(); i++) {
            Criterion c = criteria.get(i);
            if (c == null) {
                throw new AiParseException(
                        "RubricGenerator output: criteria[" + i + "] is null. LLM text: " + snippet);
            }
            if (c.key() == null || c.key().isBlank() || !c.key().matches("^[a-z0-9_]+$")) {
                throw new AiParseException(
                        "RubricGenerator output: criteria[" + i + "].key must be snake_case (a-z, 0-9, _). LLM text: " + snippet);
            }
            if (!seenKeys.add(c.key())) {
                throw new AiParseException(
                        "RubricGenerator output: duplicate criterion key '" + c.key() + "'. LLM text: " + snippet);
            }
            if (c.name() == null || c.name().isBlank() || c.name().length() > 160) {
                throw new AiParseException(
                        "RubricGenerator output: criteria[" + i + "].name must be ≤160 chars. LLM text: " + snippet);
            }
            if (c.weight() == null
                    || c.weight().doubleValue() < 0.0
                    || c.weight().doubleValue() > 100.0) {
                double got = c.weight() == null ? -1.0 : c.weight().doubleValue();
                throw new AiParseException(
                        "RubricGenerator output: criteria[" + i + "].weight must be 0..100, got " + got
                                + ". LLM text: " + snippet);
            }
            weightSum += c.weight().doubleValue();
            if (c.descriptors() != null) {
                if (c.descriptors().size() > levels.size()) {
                    throw new AiParseException(
                            "RubricGenerator output: criteria[" + i + "].descriptors has "
                                    + c.descriptors().size() + " items, more than levels count " + levels.size()
                                    + ". LLM text: " + snippet);
                }
                for (int j = 0; j < c.descriptors().size(); j++) {
                    Descriptor d = c.descriptors().get(j);
                    if (d == null || d.level() == null) {
                        throw new AiParseException(
                                "RubricGenerator output: criteria[" + i + "].descriptors[" + j + "].level is required. LLM text: " + snippet);
                    }
                    if (!levelCodes.contains(d.level())) {
                        throw new AiParseException(
                                "RubricGenerator output: criteria[" + i + "].descriptors[" + j + "].level '"
                                        + d.level() + "' does not match any defined level ("
                                        + levelCodes + "). LLM text: " + snippet);
                    }
                    if (d.text() == null || d.text().isBlank()) {
                        throw new AiParseException(
                                "RubricGenerator output: criteria[" + i + "].descriptors[" + j + "].text is required. LLM text: " + snippet);
                    }
                }
            }
        }
        if (Math.abs(weightSum - 100.0) > 0.01) {
            throw new AiParseException(
                    "RubricGenerator output: sum of criteria.weight must be 100.0 (±0.01), got "
                            + String.format("%.4f", weightSum) + ". LLM text: " + snippet);
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? (s.length() > 200 ? s.substring(0, 200) + "..." : s) : s.substring(0, nl);
    }
}
