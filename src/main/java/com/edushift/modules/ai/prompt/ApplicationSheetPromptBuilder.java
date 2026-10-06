package com.edushift.modules.ai.prompt;

import com.edushift.modules.ai.config.ActiveLlmSettings;
import com.edushift.modules.ai.dto.GenerateApplicationSheetRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Prompt for one printable worksheet of one grade ({@code application-sheet/v1}).
 */
@Component
public class ApplicationSheetPromptBuilder {

	public static final String PROMPT_VERSION = "application-sheet/v1";

	private final ActiveLlmSettings models;

	public ApplicationSheetPromptBuilder(ActiveLlmSettings models) {
		this.models = models;
	}

	public LlmRequest build(GenerateApplicationSheetRequest request) {
		return new LlmRequest(
				models.defaultModel(),
				systemPrompt(request.kind()),
				userPrompt(request),
				0.3,
				4096,
				null,
				Map.of(
						"response_format", Map.of("type", "json_object"),
						"thinking", Map.of("type", "disabled")
				)
		);
	}

	private static String systemPrompt(ApplicationSheetKind kind) {
		String shape = kind == ApplicationSheetKind.DIAGNOSTIC
				? """
				{
				  "title": "string 5..200",
				  "subtitle": "string",
				  "situation": "situación significativa para este grado",
				  "competencyMatrix": [{"competency":"string","criterion":"string"}],
				  "tasks": [{"competency":"string","title":"string","stimulus":"string","items":[{"kind":"LINE|CHECKBOX|CHOICE","prompt":"string","choices":["solo si CHOICE"]}]}],
				  "achievementLevels": [{"competency":"string","logro":"AD/A","proceso":"B","inicio":"C"}],
				  "teacherComment": true
				}
				"""
				: """
				{
				  "title": "string 5..200",
				  "warmup": {"prompt":"¿Cómo me siento hoy?","options":["Alegre","Sorprendido","Nervioso","Tranquilo"]},
				  "purpose": {"purpose":"string","evidence":"string","criterion":"un criterio, el de este grado"},
				  "activities": [{"title":"string","moment":"string","instruction":"string","items":[{"kind":"LINE|CHECKBOX|CHOICE","prompt":"string","choices":["solo si CHOICE"]}]}],
				  "selfAssessment": {"prompt":"string","columns":["Lo logré","Necesito ayuda"],"rows":["string"]},
				  "reflection": {"questions":[{"kind":"LINE|CHOICE","prompt":"string","choices":[]}]},
				  "closingMessage": "string"
				}
				""";
		return """
				Eres docente de aula en Perú. Escribes una ficha para imprimir, de UN solo grado.
				Responde solo JSON válido, sin markdown.
				No uses las claves variants ni criteriaByGrade.
				No menciones otro grado ni armes columnas para dos grados.
				Los huecos para que el alumno escriba son ítems kind=LINE.
				Las casillas son kind=CHECKBOX. Las opciones marcables son kind=CHOICE.
				El texto debe caber en una hoja A4.
				Esquema:
				""" + shape;
	}

	private static String userPrompt(GenerateApplicationSheetRequest request) {
		String activities = request.activities().stream()
				.map(a -> "- [" + nullToDash(a.phase()) + "] " + a.name() + ": " + nullToDash(a.description()))
				.collect(Collectors.joining("\n"));
		String competencies = request.competencyNames() == null || request.competencyNames().isEmpty()
				? "(no indicadas)"
				: String.join("; ", request.competencyNames());
		String criteria = request.evaluationCriteria() == null || request.evaluationCriteria().isEmpty()
				? "(no indicados)"
				: request.evaluationCriteria().stream()
						.map(c -> "- " + c.name() + ": " + nullToDash(c.description()))
						.collect(Collectors.joining("\n"));
		return """
				Tipo: %s
				Grado (único): %s
				Curso: %s
				Tema: %s
				Título de la sesión: %s
				Resumen: %s
				Competencias: %s
				Criterios de la sesión:
				%s
				Actividades de la sesión:
				%s
				Redacta la ficha solo para %s, alineada a esas actividades.
				Si el resumen menciona otro nivel (Inicial, Primaria o Secundaria), ignóralo.
				La ficha es únicamente para el grado indicado.
				""".formatted(
				request.kind().name(),
				request.grade().trim(),
				request.courseName().trim(),
				nullToDash(request.topic()),
				request.sessionTitle().trim(),
				nullToDash(request.sessionSummary()),
				competencies,
				criteria,
				activities,
				request.grade().trim());
	}

	private static String nullToDash(String value) {
		return value == null || value.isBlank() ? "-" : value.trim();
	}
}
