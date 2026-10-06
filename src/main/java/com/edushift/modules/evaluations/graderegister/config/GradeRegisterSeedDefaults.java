package com.edushift.modules.evaluations.graderegister.config;

import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Static catalog of formal grade-register templates (F1 + planilla slots).
 * Materialized per-tenant on first {@code GET .../catalog}.
 */
public final class GradeRegisterSeedDefaults {

	private GradeRegisterSeedDefaults() {
		throw new AssertionError("config class — not instantiable");
	}

	public record DefaultCategory(
			String name,
			String colorToken,
			int sortOrder,
			BigDecimal weight,
			boolean contributesToAverage,
			boolean allowsTeacherColumns,
			GradeCategoryColumnMode columnMode,
			Integer slotCount,
			String slotLabelPrefix,
			List<String> slotLabels
	) {
	}

	public record DefaultTemplate(
			String catalogCode,
			String name,
			String description,
			List<String> recommendedLevels,
			List<DefaultCategory> categories
	) {
	}

	public static List<DefaultTemplate> all() {
		return List.of(
				new DefaultTemplate(
						"STD_BIMESTRE",
						"Registro estándar bimestre",
						"Participación, tareas y exámenes con pesos 30/30/40. El docente crea las columnas. Registro interno 0–20 (no es acta SIAGIE).",
						List.of("PRIMARIA", "SECUNDARIA"),
						List.of(
								open("Participación", "amber", 1, "30", true, true),
								open("Tareas", "green", 2, "30", true, true),
								open("Exámenes", "blue", 3, "40", true, true)
						)
				),
				new DefaultTemplate(
						"CON_CONDUCTA",
						"Registro con conducta",
						"Participación, exámenes y conducta institucional. El docente crea las columnas.",
						List.of("PRIMARIA", "SECUNDARIA"),
						List.of(
								open("Participación", "amber", 1, "30", true, true),
								open("Exámenes", "blue", 2, "50", true, true),
								open("Conducta", "slate", 3, "20", true, false)
						)
				),
				new DefaultTemplate(
						"COMPETENCIAS",
						"Registro por competencias",
						"Categorías por competencia; literales oficiales SIAGIE (AD/A/B/C) fuera del promedio numérico hasta DEBT-EVAL-8.",
						List.of("INICIAL", "PRIMARIA"),
						List.of(
								open("Competencia 1", "violet", 1, "34", true, true),
								open("Competencia 2", "violet", 2, "33", true, true),
								open("Competencia 3", "violet", 3, "33", true, true)
						)
				),
				new DefaultTemplate(
						"PLANILLA_SESIONES",
						"Registro tipo planilla / sesiones",
						"Bloque con S1–S10 (Participación oral) + columnas sueltas. Típico secundaria; también usable en primaria privada.",
						List.of("SECUNDARIA", "PRIMARIA"),
						List.of(
								series("Participación oral", "amber", 1, "30", true, true, 10, "S"),
								single("Conducta", "slate", 2, "10", true, false),
								single("Presentación", "green", 3, "10", true, false),
								single("Examen semanal", "blue", 4, "15", true, false),
								single("Examen mensual", "blue", 5, "15", true, false),
								single("Examen bimestral", "blue", 6, "20", true, false)
						)
				)
		);
	}

	private static DefaultCategory open(
			String name,
			String color,
			int order,
			String weight,
			boolean contributes,
			boolean allowsTeacher
	) {
		return new DefaultCategory(
				name, color, order, new BigDecimal(weight), contributes, allowsTeacher,
				GradeCategoryColumnMode.OPEN, null, "S", null);
	}

	private static DefaultCategory series(
			String name,
			String color,
			int order,
			String weight,
			boolean contributes,
			boolean allowsTeacher,
			int slotCount,
			String prefix
	) {
		return new DefaultCategory(
				name, color, order, new BigDecimal(weight), contributes, allowsTeacher,
				GradeCategoryColumnMode.SERIES, slotCount, prefix, null);
	}

	private static DefaultCategory single(
			String name,
			String color,
			int order,
			String weight,
			boolean contributes,
			boolean allowsTeacher
	) {
		return new DefaultCategory(
				name, color, order, new BigDecimal(weight), contributes, allowsTeacher,
				GradeCategoryColumnMode.SINGLE, 1, "S", null);
	}
}
