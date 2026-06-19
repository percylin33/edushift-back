package com.edushift.modules.ai.prompt;

import com.edushift.modules.ai.config.OpenRouterProperties;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link LlmRequest} for the "suggest quiz questions" use case
 * (BE-7c.1). The system prompt is versioned in source so we can
 * track which prompt produced which {@code ai_generations.response_parsed}
 * row (ai-rules.mdc §PROMPT RULES).
 *
 * <h3>Output contract (instructed to the model)</h3>
 * <p>We constrain the model to emit a strict JSON object
 * (no markdown, no preamble) that we can parse deterministically.
 * The shape matches the {@code QuestionSuggestion} DTO 1:1, so the
 * parser is a straight Jackson deserialisation.</p>
 *
 * <pre>{@code
 * {
 *   "questions": [
 *     {
 *       "prompt":    "¿Cuál es la capital de Francia?",
 *       "type":      "MC" | "TF" | "SHORT_ANSWER",
 *       "points":    5,
 *       "options":   [ { "label": "...", "isCorrect": true, "explanation": "..." } ],
 *       "rationale": "..."
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h3>Why few-shot</h3>
 * <p>Empirically, small open models (gpt-4o-mini, claude-haiku) drift
 * out of the strict JSON shape on the first try. We pin the format
 * with 2 examples (one MC, one TF) so even the cheaper models stick
 * to it. The model is also told to emit EXACTLY {@code count} questions
 * (or fewer if the topic doesn't support more).</p>
 *
 * <h3>Why temperature=0.2</h3>
 * <p>Structured-output tasks prefer low-temperature; 0.2 is the
 * sweet spot between "boring" (0.0, all questions look the same)
 * and "noisy" (0.7+, JSON shape starts to drift).</p>
 */
@Component
public class QuizQuestionPromptBuilder {

    /** Bump this when the system prompt changes so we can correlate
     * generations in the audit log. */
    public static final String PROMPT_VERSION = "quiz-question-suggest/v1";

    private final OpenRouterProperties props;

    public QuizQuestionPromptBuilder(OpenRouterProperties props) {
        this.props = props;
    }

    /**
     * @param topic       free-form topic from the teacher (e.g. "Capitales de Europa").
     *                    Must be non-blank.
     * @param count       1..5; how many questions the model should produce.
     * @param questionType optional filter: {@code "MC" | "TF" | "SHORT_ANSWER"}.
     *                    {@code null} = any.
     * @param model       model id to use (caller passes the tenant override or
     *                    the app default). Must be non-blank.
     */
    public LlmRequest build(String topic, int count, String questionType, String model) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (count < 1 || count > 5) {
            throw new IllegalArgumentException("count must be in [1,5], got " + count);
        }
        if (model == null || model.isBlank()) {
            model = props.getDefaultModel();
        }
        return new LlmRequest(
                model,
                buildSystemPrompt(),
                buildUserPrompt(topic, count, questionType),
                Double.valueOf(0.2),
                Integer.valueOf(2048),
                null,
                Map.of("response_format", Map.of("type", "json_object"))
        );
    }

    private String buildSystemPrompt() {
        return """
                Eres un asistente pedagógico de EduShift. Tu trabajo es sugerir preguntas
                de quiz (multiple choice, verdadero/falso o respuesta corta) sobre un
                tema que indique el profesor.

                REGLAS DURAS:
                1. Respondes SIEMPRE en JSON válido y NADA más. No escribas prosa, no
                   uses bloques ```, no antepongas "Aquí está el JSON". El primer
                   carácter de tu respuesta debe ser "{" y el último debe ser "}".
                2. La raíz del JSON es un objeto con la clave "questions" (array).
                   Puedes emitir entre 1 y 5 preguntas, según el campo COUNT.
                3. Cada pregunta tiene exactamente estos campos:
                     - "prompt"    : enunciado de la pregunta (string, no vacío).
                     - "type"      : "MC" | "TF" | "SHORT_ANSWER".
                     - "points"    : entero entre 1 y 10 (sugerir 5 por defecto).
                     - "options"   : array de opciones. Reglas por tipo:
                         * MC              → 2 a 4 opciones, EXACTAMENTE UNA con
                                             "isCorrect": true.
                         * TF              → EXACTAMENTE 2 opciones ("Verdadero" y
                                             "Falso"), una de ellas "isCorrect": true.
                         * SHORT_ANSWER    → array VACÍO (no hay opciones).
                     - "rationale" : explicación breve de por qué esta pregunta es
                                     útil para evaluar el tema. 1 frase.
                4. Si te pasan QUESTION_TYPE_FILTER distinto de null, TODAS las
                   preguntas que emitas deben respetar ese tipo. Si es null, puedes
                   mezclar.
                5. Las preguntas deben ser apropiadas para el contexto educativo
                   hispanohablante. Nada de contenido adulto, violento o
                   desinformación.
                6. No inventes referencias bibliográficas. Si la pregunta requiere
                   datos concretos, usa solo conocimiento general.
                7. El campo "explanation" en cada opción es opcional pero recomendado
                   para MC. Pon null cuando no aplique.

                EJEMPLO 1 (COUNT=1, QUESTION_TYPE_FILTER="MC"):

                TEMA: Capitales de Europa

                {"questions":[{"prompt":"¿Cuál es la capital de Francia?","type":"MC","points":5,"options":[{"label":"París","isCorrect":true,"explanation":"Capital desde 987 d.C."},{"label":"Londres","isCorrect":false,"explanation":null},{"label":"Berlín","isCorrect":false,"explanation":null}],"rationale":"Pregunta clásica de geografía europea. Nivel básico."}]}

                EJEMPLO 2 (COUNT=1, QUESTION_TYPE_FILTER="TF"):

                TEMA: Punto de ebullición del agua

                {"questions":[{"prompt":"Verdadero o falso: el agua hierve a 100°C al nivel del mar.","type":"TF","points":3,"options":[{"label":"Verdadero","isCorrect":true,"explanation":"A 1 atm, el agua pura hierve a 100°C."},{"label":"Falso","isCorrect":false,"explanation":null}],"rationale":"Concepto físico elemental, formato rápido."}]}
                """;
    }

    private String buildUserPrompt(String topic, int count, String questionType) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("COUNT: ").append(count).append('\n');
        sb.append("QUESTION_TYPE_FILTER: ")
                .append(questionType == null ? "null" : questionType)
                .append('\n');
        sb.append("TEMA: ").append(topic.trim()).append('\n');
        return sb.toString();
    }

    /** Convenience for callers that want to know the version we are on. */
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    /** Whitelist of allowed question types (for validation; the model can also
     * produce invalid types, in which case the parser throws and the LLM call
     * is logged as FAILED). */
    public static List<String> allowedQuestionTypes() {
        return List.of("MC", "TF", "SHORT_ANSWER");
    }
}
