package com.edushift.modules.sessions.template.config;

import com.edushift.modules.sessions.template.entity.SessionTemplateFamily;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static MINEDU archetypes materialised per-tenant on first catalog access.
 * Mirrors the V66 seed content so schools no longer depend on ROOT-tenant rows.
 */
public final class SessionTemplateSeedDefaults {

	private SessionTemplateSeedDefaults() {
		throw new AssertionError("config class — not instantiable");
	}

	public record DefaultTemplate(
			String templateKey,
			String name,
			String description,
			SessionTemplateFamily family,
			Map<String, Object> schema
	) {
	}

	public static List<DefaultTemplate> all() {
		return List.of(standard());
	}

	private static DefaultTemplate standard() {
		return new DefaultTemplate(
				"minedu-standard",
				"Estándar MINEDU",
				"Estructura pedagógica estándar del MINEDU: inicio, desarrollo y cierre.",
				SessionTemplateFamily.STANDARD,
				schema(
						List.of(
								phase("Inicio", 15, "Motivación, saberes previos, conflicto cognitivo"),
								phase("Desarrollo", 45, "Construcción del aprendizaje, aplicación"),
								phase("Cierre", 15, "Metacognición, evaluación, retroalimentación")
						),
						List.of("dialogo", "trabajo_grupal", "trabajo_individual", "exposicion", "lectura", "escritura"),
						List.of("observacion", "lista_cotejo", "rubrica", "preguntas_orales"),
						List.of("pizarra", "cuaderno", "texto_MINEDU", "material_concreto", "audiovisual")
				)
		);
	}

	private static Map<String, Object> phase(String name, int durationMin, String description) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", name);
		m.put("duration_min", durationMin);
		m.put("description", description);
		return m;
	}

	private static Map<String, Object> schema(
			List<Map<String, Object>> phases,
			List<String> activityTypes,
			List<String> evaluationMethods,
			List<String> materials
	) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("phases", phases);
		m.put("activity_types", activityTypes);
		m.put("evaluation_methods", evaluationMethods);
		m.put("materials_suggested", materials);
		return m;
	}
}
