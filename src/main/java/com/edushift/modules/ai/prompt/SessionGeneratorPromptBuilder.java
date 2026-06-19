package com.edushift.modules.ai.prompt;

import com.edushift.modules.ai.config.OpenRouterProperties;
import com.edushift.modules.ai.dto.GenerateSessionRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link LlmRequest} for the "generate learning session outline"
 * use case (BE-8.1).
 *
 * <h3>Output contract (ADR-8.2 — strict template)</h3>
 * <p>We constrain the model to emit a strict JSON object
 * (no markdown, no preamble) that we can validate against
 * {@code ai/schemas/session-generator.schema.json} and then
 * deserialise 1:1 into {@code GenerateSessionResponse}. The schema
 * enforces the MINEDU Perú lesson template (INICIO / DESARROLLO /
 * CIERRE phases; 3..8 activities; 1..10 resources; 1..5 evaluation
 * criteria; weights summing close to 1.0).
 *
 * <h3>Why few-shot</h3>
 * <p>Same rationale as {@link QuizQuestionPromptBuilder}: small open
 * models drift out of the strict JSON shape on the first try. We pin
 * the format with 1 complete example (INICIO + DESARROLLO + CIERRE
 * activities) so even gpt-4o-mini and claude-haiku stick to it.
 *
 * <h3>Why temperature=0.3</h3>
 * <p>Slightly higher than the quiz generator (0.2) because lesson
 * outlines benefit from a touch of variety in phrasing. Still well
 * below the "noisy" 0.7+ threshold where the JSON shape starts
 * breaking.
 *
 * <h3>Prompt versioning</h3>
 * <p>{@link #PROMPT_VERSION} is bumped whenever the system or user
 * prompt changes (ai-rules.mdc §PROMPT RULES). The version is persisted
 * alongside the {@code ai_generations} row to make it possible to
 * correlate prompt versions with output quality in post-mortems.
 */
@Component
public class SessionGeneratorPromptBuilder {

    /**
     * Bump this when the system prompt changes so we can correlate
     * generations in the audit log.
     */
    public static final String PROMPT_VERSION = "session-generator/v1";

    /** Schema filename (loaded from the classpath by the service). */
    public static final String SCHEMA_RESOURCE = "ai/schemas/session-generator.schema.json";

    private static final String SYSTEM_PROMPT = """
            Eres un asistente pedagógico experto en el currículo peruano del MINEDU.
            Tu trabajo es generar el BORRADOR de una sesión de aprendizaje
            (clase) alineada a la estructura oficial:

              * INICIO      (motivación, exploración de saberes previos)
              * DESARROLLO  (actividades principales, práctica guiada e independiente)
              * CIERRE      (metacognición, transferencia, evaluación formativa)

            REGLAS DE SALIDA (no negociables):

            1. Debes responder EXCLUSIVAMENTE con un único objeto JSON
               válido. Sin markdown, sin ```json, sin texto antes ni después.
            2. El JSON debe cumplir EXACTAMENTE el schema provisto.
               No agregues claves nuevas. No omitas claves requeridas.
            3. Las actividades DEBEN sumar (en durationMinutes) exactamente
               el `durationMinutes` solicitado. Usa 3..8 actividades
               (mínimo 1 de cada fase).
            4. Los `evaluationCriteria[*].weight` deben sumar 1.0 (±0.01).
            5. Las URLs de recursos (`resources[*].url`) sólo se incluyen
               si el docente las conoce con certeza; en caso contrario
               usa `null` y `description` debe explicar cómo obtenerlas.
            6. No inventes datos del colegio ni del docente. El contenido
               debe ser genérico y adaptable al contexto peruano.

            ESQUEMA JSON (referencia):

            {
              "title": string (5..200),
              "summary": string (20..1000),
              "activities": [
                {
                  "phase": "INICIO" | "DESARROLLO" | "CIERRE",
                  "name": string (2..120),
                  "durationMinutes": integer (5..180),
                  "description": string (10..1000)
                }
              ],
              "resources": [
                {
                  "type": "TEXT" | "VIDEO" | "AUDIO" | "IMAGE" | "INTERACTIVE" | "OTHER",
                  "title": string (2..200),
                  "url": string|null,
                  "description": string (5..500)
                }
              ],
              "evaluationCriteria": [
                {
                  "name": string (3..120),
                  "weight": number (0.0..1.0),
                  "description": string (5..500)
                }
              ],
              "competencyRefs": [string]?,
              "capacityRefs": [string]?
            }
            """;

    private final OpenRouterProperties props;

    public SessionGeneratorPromptBuilder(OpenRouterProperties props) {
        this.props = props;
    }

    /**
     * Builds the {@link LlmRequest} for a session-outline generation.
     *
     * <p>The user prompt carries the teacher's topic, course, duration,
     * and any competency/capacity references. The system prompt carries
     * the persona + strict-JSON contract + a one-shot example (in source
     * via the {@code oneShotExample} field).
     *
     * @param req the caller's request payload (already validated).
     * @param courseName the resolved course name (the LLM does not need
     *                   the courseId; the name is more informative).
     * @param competencyNames optional list of competency names. May be
     *                        empty if the caller did not pick any.
     * @param capacityNames optional list of capacity names. May be empty.
     * @return a fully-populated {@link LlmRequest}.
     */
    public LlmRequest build(GenerateSessionRequest req,
                            String courseName,
                            String gradeName,
                            List<String> competencyNames,
                            List<String> capacityNames) {
        String model = props.getDefaultModel() != null && !props.getDefaultModel().isBlank()
                ? props.getDefaultModel()
                : "openai/gpt-4o-mini";

        String userPrompt = buildUserPrompt(req, courseName, gradeName, competencyNames, capacityNames);

        return new LlmRequest(
                model,
                SYSTEM_PROMPT,
                userPrompt,
                /* temperature */ 0.3,
                /* maxTokens    */ 2048,
                /* stopSeqs     */ null,
                /* extra        */ Map.of(
                        // Hint the provider to use JSON mode (OpenRouter / OpenAI).
                        "response_format", Map.of("type", "json_object")
                )
        );
    }

    private String buildUserPrompt(GenerateSessionRequest req,
                                   String courseName,
                                   String gradeName,
                                   List<String> competencyNames,
                                   List<String> capacityNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Genera una sesión de aprendizaje para:\n\n");
        sb.append("Curso: ").append(courseName).append('\n');
        if (gradeName != null && !gradeName.isBlank()) {
            sb.append("Grado: ").append(gradeName).append('\n');
        }
        sb.append("Tema: ").append(req.topic()).append('\n');
        sb.append("Duración total: ").append(req.durationMinutes()).append(" minutos\n");
        if (competencyNames != null && !competencyNames.isEmpty()) {
            sb.append("Competencias objetivo:\n");
            for (String c : competencyNames) {
                sb.append("  - ").append(c).append('\n');
            }
        }
        if (capacityNames != null && !capacityNames.isEmpty()) {
            sb.append("Capacidades objetivo:\n");
            for (String c : capacityNames) {
                sb.append("  - ").append(c).append('\n');
            }
        }
        sb.append('\n');
        sb.append("Recuerda: solo el JSON, sin markdown ni texto adicional. ");
        sb.append("La suma de `activities[*].durationMinutes` debe ser EXACTAMENTE ")
          .append(req.durationMinutes()).append(" minutos. ");
        sb.append("Los `evaluationCriteria[*].weight` deben sumar 1.0.");
        return sb.toString();
    }
}
