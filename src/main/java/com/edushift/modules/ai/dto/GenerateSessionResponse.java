package com.edushift.modules.ai.dto;

import com.edushift.modules.ai.exception.AiParseException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Response payload for {@code POST /v1/ai/generate-session} (BE-8.1).
 *
 * <p>Strict JSON contract aligned to the MINEDU Perú lesson template
 * (capacidades + actividades + recursos + evaluación). The shape is
 * enforced by {@link #validate(String, int)} after deserialisation.
 * The same shape can be fed back into a {@code POST /v1/learning-sessions}
 * call to persist it (BE-8.5, out of MVP scope).
 *
 * <h3>Example</h3>
 * <pre>{@code
 * {
 *   "title": "La Revolución Francesa: del absolutismo a la república",
 *   "summary": "Los estudiantes explorarán las causas, desarrollo y consecuencias…",
 *   "activities": [
 *     { "phase": "INICIO", "name": "Lluvia de ideas", "durationMinutes": 10, "description": "..." }
 *   ],
 *   "resources": [
 *     { "type": "VIDEO", "title": "...", "url": null, "description": "..." }
 *   ],
 *   "evaluationCriteria": [
 *     { "name": "Comprensión causal", "weight": 0.4, "description": "..." }
 *   ],
 *   "competencyRefs": ["019e..."],
 *   "capacityRefs": ["019e..."]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateSessionResponse(
        String title,
        String summary,
        List<Activity> activities,
        List<Resource> resources,
        List<EvaluationCriterion> evaluationCriteria,
        List<String> competencyRefs,
        List<String> capacityRefs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Activity(
            Phase phase,
            String name,
            int durationMinutes,
            String description
    ) {
        public enum Phase { INICIO, DESARROLLO, CIERRE }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resource(
            Type type,
            String title,
            String url,
            String description
    ) {
        public enum Type { TEXT, VIDEO, AUDIO, IMAGE, INTERACTIVE, OTHER }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluationCriterion(
            String name,
            double weight,
            String description
    ) {
    }

    /**
     * Validates the response shape against the {@code session-generator/v1}
     * contract (the one the system prompt instructs the model to emit).
     * Throws {@link AiParseException} on any structural problem.
     *
     * <p>Rules enforced:</p>
     * <ul>
     *   <li>title: required, 5..200 chars.</li>
     *   <li>summary: required, 20..1000 chars.</li>
     *   <li>activities: 3..8 items, ≥1 of each phase,
     *       sum of {@code durationMinutes} == {@code expectedDuration},
     *       each item 5..180 min.</li>
     *   <li>resources: 1..10 items, each with non-blank title and description.</li>
     *   <li>evaluationCriteria: 1..5 items, weights sum to 1.0 (±0.01).</li>
     * </ul>
     *
     * @param rawText the original LLM text (used only for error messages).
     * @param expectedDuration the {@code durationMinutes} the teacher asked for;
     *                         we require the sum of activities to match.
     */
    public void validate(String rawText, int expectedDuration) {
        String snippet = firstLine(rawText);
        // title
        if (title == null || title.isBlank()) {
            throw new AiParseException("SessionGenerator output: 'title' is required and non-blank. LLM text: " + snippet);
        }
        if (title.length() < 5 || title.length() > 200) {
            throw new AiParseException(
                    "SessionGenerator output: 'title' must be 5..200 chars, got " + title.length() + ". LLM text: " + snippet);
        }
        // summary
        if (summary == null || summary.length() < 20 || summary.length() > 1000) {
            int got = summary == null ? 0 : summary.length();
            throw new AiParseException(
                    "SessionGenerator output: 'summary' must be 20..1000 chars, got " + got + ". LLM text: " + snippet);
        }
        // activities
        if (activities == null || activities.size() < 3 || activities.size() > 8) {
            int got = activities == null ? 0 : activities.size();
            throw new AiParseException(
                    "SessionGenerator output: 'activities' must have 3..8 items, got " + got + ". LLM text: " + snippet);
        }
        Set<Activity.Phase> phasesSeen = new HashSet<>();
        int durationSum = 0;
        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            if (a == null) {
                throw new AiParseException(
                        "SessionGenerator output: activities[" + i + "] is null. LLM text: " + snippet);
            }
            if (a.phase() == null) {
                throw new AiParseException(
                        "SessionGenerator output: activities[" + i + "].phase is required. LLM text: " + snippet);
            }
            phasesSeen.add(a.phase());
            if (a.name() == null || a.name().isBlank() || a.name().length() < 2 || a.name().length() > 120) {
                throw new AiParseException(
                        "SessionGenerator output: activities[" + i + "].name must be 2..120 chars. LLM text: " + snippet);
            }
            if (a.durationMinutes() < 5 || a.durationMinutes() > 180) {
                throw new AiParseException(
                        "SessionGenerator output: activities[" + i + "].durationMinutes must be 5..180, got "
                                + a.durationMinutes() + ". LLM text: " + snippet);
            }
            if (a.description() == null || a.description().length() < 10 || a.description().length() > 1000) {
                throw new AiParseException(
                        "SessionGenerator output: activities[" + i + "].description must be 10..1000 chars. LLM text: " + snippet);
            }
            durationSum += a.durationMinutes();
        }
        if (durationSum != expectedDuration) {
            throw new AiParseException(
                    "SessionGenerator output: sum of activities.durationMinutes (" + durationSum
                            + ") must equal requested durationMinutes (" + expectedDuration + "). LLM text: " + snippet);
        }
        if (!phasesSeen.contains(Activity.Phase.INICIO)
                || !phasesSeen.contains(Activity.Phase.DESARROLLO)
                || !phasesSeen.contains(Activity.Phase.CIERRE)) {
            throw new AiParseException(
                    "SessionGenerator output: activities must include at least 1 of each phase (INICIO, DESARROLLO, CIERRE). Got "
                            + phasesSeen + ". LLM text: " + snippet);
        }
        // resources
        if (resources == null || resources.isEmpty() || resources.size() > 10) {
            int got = resources == null ? 0 : resources.size();
            throw new AiParseException(
                    "SessionGenerator output: 'resources' must have 1..10 items, got " + got + ". LLM text: " + snippet);
        }
        for (int i = 0; i < resources.size(); i++) {
            Resource r = resources.get(i);
            if (r == null || r.type() == null) {
                throw new AiParseException(
                        "SessionGenerator output: resources[" + i + "].type is required. LLM text: " + snippet);
            }
            if (r.title() == null || r.title().isBlank() || r.title().length() > 200) {
                throw new AiParseException(
                        "SessionGenerator output: resources[" + i + "].title must be 2..200 chars. LLM text: " + snippet);
            }
            if (r.url() != null && r.url().length() > 500) {
                throw new AiParseException(
                        "SessionGenerator output: resources[" + i + "].url must be ≤500 chars. LLM text: " + snippet);
            }
            if (r.description() == null || r.description().length() < 5 || r.description().length() > 500) {
                throw new AiParseException(
                        "SessionGenerator output: resources[" + i + "].description must be 5..500 chars. LLM text: " + snippet);
            }
        }
        // evaluationCriteria
        if (evaluationCriteria == null || evaluationCriteria.isEmpty() || evaluationCriteria.size() > 5) {
            int got = evaluationCriteria == null ? 0 : evaluationCriteria.size();
            throw new AiParseException(
                    "SessionGenerator output: 'evaluationCriteria' must have 1..5 items, got " + got + ". LLM text: " + snippet);
        }
        double weightSum = 0.0;
        for (int i = 0; i < evaluationCriteria.size(); i++) {
            EvaluationCriterion c = evaluationCriteria.get(i);
            if (c == null) {
                throw new AiParseException(
                        "SessionGenerator output: evaluationCriteria[" + i + "] is null. LLM text: " + snippet);
            }
            if (c.name() == null || c.name().isBlank() || c.name().length() < 3 || c.name().length() > 120) {
                throw new AiParseException(
                        "SessionGenerator output: evaluationCriteria[" + i + "].name must be 3..120 chars. LLM text: " + snippet);
            }
            if (c.weight() < 0.0 || c.weight() > 1.0) {
                throw new AiParseException(
                        "SessionGenerator output: evaluationCriteria[" + i + "].weight must be 0.0..1.0, got "
                                + c.weight() + ". LLM text: " + snippet);
            }
            if (c.description() == null || c.description().length() < 5 || c.description().length() > 500) {
                throw new AiParseException(
                        "SessionGenerator output: evaluationCriteria[" + i + "].description must be 5..500 chars. LLM text: " + snippet);
            }
            weightSum += c.weight();
        }
        if (Math.abs(weightSum - 1.0) > 0.01) {
            throw new AiParseException(
                    "SessionGenerator output: sum of evaluationCriteria.weight must be 1.0 (±0.01), got "
                            + String.format("%.4f", weightSum) + ". LLM text: " + snippet);
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? (s.length() > 200 ? s.substring(0, 200) + "..." : s) : s.substring(0, nl);
    }
}
