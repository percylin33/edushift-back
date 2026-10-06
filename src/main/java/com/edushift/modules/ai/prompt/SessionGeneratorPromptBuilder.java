package com.edushift.modules.ai.prompt;

import com.edushift.modules.ai.config.ActiveLlmSettings;
import com.edushift.modules.ai.dto.GenerateSessionRequest;
import com.edushift.modules.ai.dto.GenerateSessionResponse;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.rag.RagContextService.RagSnippet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link LlmRequest} for the "generate learning session outline"
 * use case (BE-8.1). Phases default to MINEDU INICIO/DESARROLLO/CIERRE; a
 * tenant session template can override the required phase tokens.
 */
@Component
public class SessionGeneratorPromptBuilder {

	public static final String PROMPT_VERSION = "session-generator/v2";

	public static final String SCHEMA_RESOURCE = "ai/schemas/session-generator.schema.json";

	private final ActiveLlmSettings models;

	public SessionGeneratorPromptBuilder(ActiveLlmSettings models) {
		this.models = models;
	}

	public LlmRequest build(GenerateSessionRequest req,
							String courseName,
							String gradeName,
							List<String> competencyNames,
							List<String> capacityNames) {
		return build(req, courseName, gradeName, competencyNames, capacityNames,
				GenerateSessionResponse.DEFAULT_PHASES);
	}

	public LlmRequest build(GenerateSessionRequest req,
							String courseName,
							String gradeName,
							List<String> competencyNames,
							List<String> capacityNames,
							List<String> phaseTokens) {
		List<String> phases = (phaseTokens == null || phaseTokens.isEmpty())
				? GenerateSessionResponse.DEFAULT_PHASES
				: phaseTokens;

		String model = models.defaultModel();

		String userPrompt = buildUserPrompt(req, courseName, gradeName, competencyNames, capacityNames, phases);

		return new LlmRequest(
				model,
				systemPrompt(phases),
				userPrompt,
				0.3,
				2048,
				null,
				Map.of(
						"response_format", Map.of("type", "json_object"),
						"thinking", Map.of("type", "disabled")
				)
		);
	}

	public LlmRequest buildWithRag(GenerateSessionRequest req,
									String courseName,
									String gradeName,
									List<String> competencyNames,
									List<String> capacityNames,
									List<RagSnippet> ragSnippets) {
		return buildWithRag(req, courseName, gradeName, competencyNames, capacityNames,
				ragSnippets, GenerateSessionResponse.DEFAULT_PHASES);
	}

	public LlmRequest buildWithRag(GenerateSessionRequest req,
									String courseName,
									String gradeName,
									List<String> competencyNames,
									List<String> capacityNames,
									List<RagSnippet> ragSnippets,
									List<String> phaseTokens) {
		LlmRequest base = build(req, courseName, gradeName, competencyNames, capacityNames, phaseTokens);
		if (ragSnippets == null || ragSnippets.isEmpty()) {
			return base;
		}
		String augmented = injectContext(base.userPrompt(), ragSnippets);
		return new LlmRequest(
				base.model(),
				base.systemPrompt(),
				augmented,
				base.temperature(),
				base.maxTokens(),
				base.stopSequences(),
				base.extra(),
				base.history());
	}

	private static String systemPrompt(List<String> phases) {
		String phaseList = phases.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(" | "));
		String phaseBullets = phases.stream()
				.map(p -> "              * " + p)
				.collect(Collectors.joining("\n"));
		return """
				Eres un asistente pedagógico experto en el currículo peruano del MINEDU.
				Tu trabajo es generar el BORRADOR de una sesión de aprendizaje
				(clase) alineada a la estructura del colegio:

				%s

				REGLAS DE SALIDA (no negociables):

				1. Debes responder EXCLUSIVAMENTE con un único objeto JSON
				   válido. Sin markdown, sin ```json, sin texto antes ni después.
				2. El JSON debe cumplir EXACTAMENTE el schema provisto.
				   No agregues claves nuevas. No omitas claves requeridas.
				3. Las actividades DEBEN sumar (en durationMinutes) exactamente
				   el `durationMinutes` solicitado. Usa al menos una actividad
				   por cada fase requerida (%s).
				   `activities[*].description` NO es el nombre de la fase:
				   escribe 2 a 4 oraciones en español con qué hace el docente,
				   qué hacen los estudiantes y qué producto queda.
				4. Los `evaluationCriteria[*].weight` deben sumar 1.0 (±0.01).
				5. Las URLs de recursos (`resources[*].url`) sólo se incluyen
				   si el docente las conoce con certeza; en caso contrario
				   usa `null` y `description` debe explicar cómo obtenerlas.
				6. No inventes datos del colegio ni del docente. El contenido
				   debe ser genérico y adaptable al contexto peruano.
				7. El campo `activities[*].phase` DEBE ser exactamente uno de:
				   %s

				ESQUEMA JSON (referencia):

				{
				  "title": string (5..200),
				  "summary": string (20..1000),
				  "activities": [
				    {
				      "phase": %s,
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
				""".formatted(phaseBullets, String.join(", ", phases), phaseList, phaseList);
	}

	private static String injectContext(String userPrompt, List<RagSnippet> snippets) {
		StringBuilder sb = new StringBuilder();
		sb.append("[CONTEXTO RECUPERADO DEL CURSO]\n");
		for (RagSnippet s : snippets) {
			sb.append("- (").append(s.kind()).append(") ")
					.append(s.title()).append(": ")
					.append(s.text()).append('\n');
		}
		sb.append("\nUsa el contexto anterior cuando sea relevante para el tema. ")
				.append("Si el contexto no es relevante, ignóralo.\n\n");
		sb.append(userPrompt);
		return sb.toString();
	}

	private String buildUserPrompt(GenerateSessionRequest req,
								   String courseName,
								   String gradeName,
								   List<String> competencyNames,
								   List<String> capacityNames,
								   List<String> phases) {
		StringBuilder sb = new StringBuilder();
		sb.append("Genera una sesión de aprendizaje para:\n\n");
		sb.append("Curso: ").append(courseName).append('\n');
		if (gradeName != null && !gradeName.isBlank()) {
			sb.append("Grado de la sección (obligatorio): ").append(gradeName.trim()).append('\n');
			sb.append("Redacta el objetivo, las actividades y los criterios SOLO para ese grado y su nivel. ");
			sb.append("Si el curso también se dicta en otros niveles, ignóralos. ");
			sb.append("No escribas para Inicial cuando el grado es de Primaria o Secundaria, ");
			sb.append("ni para Primaria cuando el grado es de Inicial.\n");
		}
		sb.append("Tema: ").append(req.topic()).append('\n');
		sb.append("Duración total: ").append(req.durationMinutes()).append(" minutos\n");
		sb.append("Fases obligatorias: ").append(String.join(", ", phases)).append('\n');
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
		sb.append("Los `evaluationCriteria[*].weight` deben sumar 1.0. ");
		sb.append("Cada description de actividad debe narrar la dinámica de clase, ");
		sb.append("no repetir solo el nombre de la fase.");
		return sb.toString();
	}
}
