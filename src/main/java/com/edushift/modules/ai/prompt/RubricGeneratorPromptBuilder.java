package com.edushift.modules.ai.prompt;

import com.edushift.modules.ai.config.OpenRouterProperties;
import com.edushift.modules.ai.dto.GenerateRubricRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link LlmRequest} for the "generate rubric" use case
 * (BE-8.2).
 *
 * <h3>Output contract (ADR-8.2 — strict template)</h3>
 * <p>Same strategy as {@link SessionGeneratorPromptBuilder}: we instruct
 * the model to emit a strict JSON object that matches
 * {@code GenerateRubricResponse} 1:1, then validate with
 * {@code GenerateRubricResponse.validate()} after Jackson
 * deserialisation.</p>
 *
 * <h3>Fork (ADR-8.3)</h3>
 * <p>If {@code request.seedRubricId} is set, the prompt names the seed
 * (e.g. "Estás adaptando la rúbrica MINEDU 'Ensayo Argumentativo'")
 * so the AI works in the same conceptual space. The actual fork
 * (persisting a new {@code Rubric} with {@code parentRubricId} set)
 * is the FE's job — the endpoint returns the JSON and the FE does
 * {@code POST /v1/academic/rubrics} if it accepts the preview.</p>
 *
 * <h3>Why temperature=0.2</h3>
 * <p>Rubrics need very low variance. Weights, level codes and
 * descriptor text must be stable; 0.2 keeps the LLM close to its
 * prior.</p>
 */
@Component
public class RubricGeneratorPromptBuilder {

    /** Bump this when the system prompt changes (ai-rules.mdc §PROMPT RULES). */
    public static final String PROMPT_VERSION = "rubric-generator/v1";

    private static final String SYSTEM_PROMPT = """
            Eres un asistente pedagógico experto en diseño de rúbricas de
            evaluación alineadas al currículo peruano del MINEDU.

            Tu trabajo es generar el BORRADOR de una rúbrica analítica con
            2..4 niveles de logro y N criterios ponderados (la suma de los
            pesos debe ser 100.0).

            REGLAS DE SALIDA (no negociables):

            1. Debes responder EXCLUSIVAMENTE con un único objeto JSON
               válido. Sin markdown, sin ```json, sin texto antes ni después.
            2. El JSON debe cumplir EXACTAMENTE el schema provisto.
               No agregues claves nuevas. No omitas claves requeridas.
            3. La suma de `criteria[*].weight` debe ser exactamente 100.0 (±0.01).
            4. Las claves (`criteria[*].key`) deben ser snake_case (a-z, 0-9, _),
               únicas dentro de la rúbrica, ≤64 chars.
            5. Los `levels[*].code` deben ser únicos y no vacíos. Para
               rúbricas canónicas MINEDU usa:
               EN_INICIO, EN_PROCESO, LOGRO_ESPERADO, LOGRO_DESTACADO.
            6. `levels[*].order` debe ir de 0 a N-1.
            7. `descriptors[*].level` debe referenciar un `level.code` definido.
               Cada criterio puede tener 0..N descriptores (uno por nivel).
            8. Los pesos sugeridos deben ser realistas y no degenerar
               (nada de un criterio con peso 100 y los demás con 0).

            ESQUEMA JSON (referencia):

            {
              "name": string (2..160),
              "description": string|null (≤2000),
              "criteria": [
                {
                  "key": string (snake_case, ≤64),
                  "name": string (≤160),
                  "description": string|null (≤1000),
                  "weight": number (0..100),
                  "descriptors": [
                    { "level": string, "text": string (≤1000) }
                  ]?
                }
              ],
              "levels": [
                {
                  "code": string (≤64, único),
                  "name": string (≤120),
                  "order": integer (0..N-1)
                }
              ]
            }
            """;

    private final OpenRouterProperties props;

    public RubricGeneratorPromptBuilder(OpenRouterProperties props) {
        this.props = props;
    }

    public LlmRequest build(GenerateRubricRequest request,
                            String seedRubricName,
                            String seedRubricCriteriaSummary) {
        String model = props.getDefaultModel() != null && !props.getDefaultModel().isBlank()
                ? props.getDefaultModel()
                : "openai/gpt-4o-mini";

        String userPrompt = buildUserPrompt(request, seedRubricName, seedRubricCriteriaSummary);

        return new LlmRequest(
                model,
                SYSTEM_PROMPT,
                userPrompt,
                /* temperature */ 0.2,
                /* maxTokens    */ 2048,
                /* stopSeqs     */ null,
                /* extra        */ Map.of(
                        "response_format", Map.of("type", "json_object")
                )
        );
    }

    private String buildUserPrompt(GenerateRubricRequest request,
                                   String seedRubricName,
                                   String seedRubricCriteriaSummary) {
        int levelCount = request.effectiveLevelCount();
        StringBuilder sb = new StringBuilder();
        sb.append("Genera una rúbrica analítica con ").append(levelCount)
          .append(" niveles de logro y ").append(request.criteria().size())
          .append(" criterios.\n\n");
        sb.append("Nombre sugerido: ").append(request.name()).append('\n');
        if (request.description() != null && !request.description().isBlank()) {
            sb.append("Descripción: ").append(request.description()).append('\n');
        }
        if (seedRubricName != null) {
            sb.append('\n').append("Estás adaptando la rúbrica existente: '")
              .append(seedRubricName).append("'.\n");
            if (seedRubricCriteriaSummary != null && !seedRubricCriteriaSummary.isBlank()) {
                sb.append("Sus criterios originales son:\n").append(seedRubricCriteriaSummary)
                  .append("\nMejora o reemplaza según el tema.\n");
            }
        }
        sb.append("\nCriterios a evaluar (en orden de importancia sugerido):\n");
        for (int i = 0; i < request.criteria().size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(request.criteria().get(i)).append('\n');
        }
        sb.append("\nRecuerda:\n");
        sb.append("- La suma de `criteria[*].weight` debe ser EXACTAMENTE 100.0.\n");
        sb.append("- Genera EXACTAMENTE ").append(levelCount).append(" niveles.\n");
        sb.append("- Genera un descriptor por nivel para cada criterio (no más).\n");
        sb.append("- Devuelve SOLO el JSON, sin markdown ni texto adicional.");
        return sb.toString();
    }
}
