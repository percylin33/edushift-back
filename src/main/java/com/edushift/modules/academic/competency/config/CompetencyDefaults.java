package com.edushift.modules.academic.competency.config;

import java.util.List;
import java.util.Map;

/**
 * Static MINEDU-style minimal catalog of competencies + capacities,
 * keyed by {@code Course.code}.
 *
 * <p>Used by {@code CompetencySeedService.seedForCourse(course)}: when
 * the TENANT_ADMIN clicks "Cargar catálogo MINEDU" on a course detail
 * page, the service looks up the course's {@code code} here and inserts
 * the bundle if (and only if) the course has zero competencies yet.</p>
 *
 * <h3>Why static and not externalised to YAML / DB?</h3>
 * <ol>
 *   <li><strong>Tiny set</strong>: 4-6 entries today; YAML overhead not worth it.</li>
 *   <li><strong>Per-tenant editable post-seed</strong>: once seeded, the
 *       admin can edit / add / remove freely. The catalog is a kickstart,
 *       not a runtime contract.</li>
 *   <li><strong>Future XLSX import</strong> ({@code DEBT-SES-3}): when a
 *       Cambridge / IB school needs a fully custom catalog, a separate
 *       importer will land in Sprint 8+ and bypass this class entirely.</li>
 * </ol>
 *
 * <h3>Curriculum reference</h3>
 * Subset of MINEDU's "Currículo Nacional de Educación Básica" (CNEB),
 * Áreas Comunicación + Matemática (Educación Primaria). Names trimmed
 * for FE display; full descriptions are intentionally short here so the
 * admin sees a clean picklist (the school can flesh them out later).
 */
public final class CompetencyDefaults {

	private CompetencyDefaults() {
		throw new AssertionError("config class — not instantiable");
	}

	/**
	 * Returns the seed bundle for the given course code, or {@code null}
	 * if the code is not known. Callers should treat {@code null} as
	 * "course not supported" (the seed endpoint returns
	 * {@code unsupportedCourseCode=true} in that case).
	 */
	public static List<DefaultCompetency> bundleFor(String courseCode) {
		if (courseCode == null) return null;
		return BUNDLES.get(courseCode.trim().toUpperCase());
	}

	public record DefaultCompetency(
			String code,
			String name,
			String description,
			List<DefaultCapacity> capacities
	) {
	}

	public record DefaultCapacity(
			String code,
			String name,
			String description
	) {
	}

	private static final Map<String, List<DefaultCompetency>> BUNDLES = Map.ofEntries(

			Map.entry("MAT", List.of(
					new DefaultCompetency(
							"MAT_C1",
							"Resuelve problemas de cantidad",
							"Plantea y resuelve problemas que demandan construir y comprender las nociones de número, sistemas numéricos y sus operaciones.",
							List.of(
									new DefaultCapacity(
											"MAT_C1_TRADUCE",
											"Traduce cantidades a expresiones numéricas",
											null),
									new DefaultCapacity(
											"MAT_C1_COMUNICA",
											"Comunica su comprensión sobre los números y las operaciones",
											null)
							)
					),
					new DefaultCompetency(
							"MAT_C2",
							"Resuelve problemas de regularidad, equivalencia y cambio",
							"Plantea y resuelve problemas en los que aparecen regularidades, equivalencias y relaciones de cambio.",
							List.of(
									new DefaultCapacity(
											"MAT_C2_TRADUCE_DATOS",
											"Traduce datos y condiciones a expresiones algebraicas",
											null)
							)
					)
			)),

			Map.entry("COMU", List.of(
					new DefaultCompetency(
							"COMU_C1",
							"Lee diversos tipos de textos escritos en su lengua materna",
							"Construye sentido a partir de los textos que lee, considerando el propósito y el contexto sociocultural.",
							List.of(
									new DefaultCapacity(
											"COMU_C1_OBTIENE",
											"Obtiene información del texto escrito",
											null),
									new DefaultCapacity(
											"COMU_C1_INFIERE",
											"Infiere e interpreta información del texto",
											null)
							)
					),
					new DefaultCompetency(
							"COMU_C2",
							"Escribe diversos tipos de textos en su lengua materna",
							"Produce textos escritos coherentes y cohesionados según el contexto sociocultural.",
							List.of(
									new DefaultCapacity(
											"COMU_C2_ADECUA",
											"Adecúa el texto a la situación comunicativa",
											null)
							)
					)
			))
	);

	/**
	 * Sorted set of supported course codes. Useful for FE hints
	 * ("este curso aún no tiene catálogo predeterminado") and for tests.
	 */
	public static List<String> supportedCourseCodes() {
		return BUNDLES.keySet().stream().sorted().toList();
	}
}
