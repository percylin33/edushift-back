package com.edushift.modules.evaluations.rubric.config;

import java.util.List;
import java.util.Map;

/**
 * Static MINEDU-style baseline library of {@code Rubric} templates
 * (Sprint 5B / BE-5B.2). Materialized on the first
 * {@code GET /academic/rubrics/system} per tenant (ADR-5B.10).
 *
 * <p>Each entry maps to one row in the {@code edushift.rubrics} table
 * with {@code is_system = true} and {@code parent_rubric_id = NULL}.
 * Tenants can fork any of these via {@code POST /rubrics/{uuid}/fork} —
 * the originals stay read-only.</p>
 *
 * <h3>Why static and not externalised to YAML / DB?</h3>
 * <ol>
 *   <li><strong>Tiny set</strong>: 8-10 entries today; YAML overhead
 *       not worth it.</li>
 *   <li><strong>Per-tenant editable post-seed</strong>: once seeded,
 *       the tenant can edit / fork / delete freely. The catalog is a
 *       kickstart, not a runtime contract.</li>
 *   <li><strong>Future AI-generated rubrics</strong> ({@code Sprint 8}
 *       / module {@code ai/}): when an IA starts producing rubrics, a
 *       separate generation endpoint will land and bypass this class
 *       entirely.</li>
 * </ol>
 *
 * <h3>Curriculum reference</h3>
 * Subset of MINEDU's "Curriculo Nacional de Educacion Basica" (CNEB).
 * Names trimmed for FE display; the school can fork and adjust freely.
 */
public final class RubricSeedDefaults {

	private RubricSeedDefaults() {
		throw new AssertionError("config class — not instantiable");
	}

	/**
	 * Returns the ordered list of seed rubrics. The seed service
	 * iterates this list in order; ordering matters for the FE library
	 * (alphabetical) but is not enforced at the DB level.
	 */
	public static List<DefaultRubric> all() {
		return BUNDLES;
	}

	public static int size() {
		return BUNDLES.size();
	}

	/**
	 * True if a rubric with the given name (case-insensitive) is in
	 * the seed library. Used by the seed to skip rows that are already
	 * there, so re-running the seed is idempotent.
	 */
	public static boolean containsName(String name) {
		if (name == null) return false;
		String normalised = name.trim();
		return BUNDLES.stream()
				.anyMatch(r -> r.name().equalsIgnoreCase(normalised));
	}

	/**
	 * Number of seed rubrics that match the given name (case-insensitive).
	 * Used by the seed's idempotency check.
	 */
	public static long countByName(String name) {
		if (name == null) return 0;
		String normalised = name.trim();
		return BUNDLES.stream()
				.filter(r -> r.name().equalsIgnoreCase(normalised))
				.count();
	}

	// =========================================================================
	// Types
	// =========================================================================

	public record DefaultCriterion(
			String key,
			String name,
			String description,
			java.math.BigDecimal weight,
			List<DefaultDescriptor> descriptors
	) {
	}

	public record DefaultDescriptor(
			String level,
			String text
	) {
	}

	public record DefaultRubric(
			String name,
			String description,
			List<DefaultCriterion> criteria,
			List<DefaultLevel> levels
	) {
	}

	public record DefaultLevel(
			String code,
			String name,
			int order
	) {
	}

	// =========================================================================
	// Static catalog (10 entries)
	// =========================================================================

	private static final List<DefaultRubric> BUNDLES = List.of(

			// ----------------------------------------------------------------
			// 1. Ensayo argumentativo (Comunicacion, secundaria)
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Ensayo argumentativo",
					"Evalua la capacidad del estudiante para construir un argumento "
							+ "coherente y sustentado en evidencia.",
					List.of(
							new DefaultCriterion("tesis", "Tesis",
									"Claridad y posicion del argumento central.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO",
													"La tesis es ausente o no se identifica."),
											new DefaultDescriptor("EN_PROCESO",
													"La tesis existe pero es ambigua."),
											new DefaultDescriptor("ESPERADO",
													"La tesis es clara y se mantiene a lo largo del texto."),
											new DefaultDescriptor("SOBRESALIENTE",
													"La tesis es original, precisa y provocadora."))),
							new DefaultCriterion("argumentos", "Argumentos y evidencia",
									"Calidad y pertinencia de los argumentos y su soporte.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO",
													"Argumentos sin evidencia o evidencia irrelevante."),
											new DefaultDescriptor("EN_PROCESO",
													"Argumentos con evidencia debil o parcial."),
											new DefaultDescriptor("ESPERADO",
													"Argumentos solidos con evidencia pertinente."),
											new DefaultDescriptor("SOBRESALIENTE",
													"Argumentos originales con evidencia multiple y critica."))),
							new DefaultCriterion("organizacion", "Organizacion",
									"Estructura parrafo a parrafo, cohesion y flujo.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO",
													"Texto desorganizado, sin parrafos claros."),
											new DefaultDescriptor("EN_PROCESO",
													"Organizacion basica con transiciones debiles."),
											new DefaultDescriptor("ESPERADO",
													"Buena organizacion con transiciones claras."),
											new DefaultDescriptor("SOBRESALIENTE",
													"Organizacion impecable con progresion argumentativa."))),
							new DefaultCriterion("redaccion", "Redaccion",
									"Ortografia, gramatica, variedad lexical.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO",
													"Errores frecuentes que dificultan la comprension."),
											new DefaultDescriptor("EN_PROCESO",
													"Errores ocasionales que no impiden la comprension."),
											new DefaultDescriptor("ESPERADO",
													"Redaccion clara con errores minimos."),
											new DefaultDescriptor("SOBRESALIENTE",
													"Redaccion precisa, fluida y estilisticamente cuidada."))),
							new DefaultCriterion("conclusiones", "Conclusiones",
									"Sintesis y proyeccion final del argumento.",
									java.math.BigDecimal.valueOf(10),
									List.of(
											new DefaultDescriptor("EN_INICIO",
													"Conclusion ausente o que contradice la tesis."),
											new DefaultDescriptor("EN_PROCESO",
													"Conclusion superficial que no sintetiza."),
											new DefaultDescriptor("ESPERADO",
													"Conclusion que retoma la tesis y proyecta."),
											new DefaultDescriptor("SOBRESALIENTE",
													"Conclusion que abre nuevas lineas de reflexion.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 2. Exposicion oral
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Exposicion oral",
					"Evalua la presentacion oral del estudiante ante el grupo.",
					List.of(
							new DefaultCriterion("claridad", "Claridad",
									"Articulacion, volumen, ritmo.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Habla incomprensible o inaudible."),
											new DefaultDescriptor("EN_PROCESO", "Articulacion irregular."),
											new DefaultDescriptor("ESPERADO", "Articulacion clara y ritmo adecuado."),
											new DefaultDescriptor("SOBRESALIENTE", "Voz proyectada, ritmo variado y preciso."))),
							new DefaultCriterion("dominio_tema", "Dominio del tema",
									"Profundidad y precision del contenido.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Lee sin comprender."),
											new DefaultDescriptor("EN_PROCESO", "Conoce el tema pero duda."),
											new DefaultDescriptor("ESPERADO", "Domina el tema y responde preguntas basicas."),
											new DefaultDescriptor("SOBRESALIENTE", "Domina y enriquece con conexiones."))),
							new DefaultCriterion("apoyo_visual", "Apoyo visual",
									"Calidad y uso del material de apoyo.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin apoyo o apoyo inutilizable."),
											new DefaultDescriptor("EN_PROCESO", "Apoyo basico con errores."),
											new DefaultDescriptor("ESPERADO", "Apoyo claro y bien utilizado."),
											new DefaultDescriptor("SOBRESALIENTE", "Apoyo diseno profesional, integrado al discurso."))),
							new DefaultCriterion("interaccion", "Interaccion con la audiencia",
									"Contacto visual, respuesta a preguntas.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No mira a la audiencia."),
											new DefaultDescriptor("EN_PROCESO", "Contacto visual limitado."),
											new DefaultDescriptor("ESPERADO", "Buen contacto visual y responde con claridad."),
											new DefaultDescriptor("SOBRESALIENTE", "Conecta con la audiencia y genera dialogo."))),
							new DefaultCriterion("tiempo", "Gestion del tiempo",
									"Ajuste al tiempo asignado.",
									java.math.BigDecimal.valueOf(10),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Excede o no llega a la mitad del tiempo."),
											new DefaultDescriptor("EN_PROCESO", "Se ajusta con ayuda."),
											new DefaultDescriptor("ESPERADO", "Se ajusta al tiempo asignado."),
											new DefaultDescriptor("SOBRESALIENTE", "Ajusta con margen y respeta tiempos de otros.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 3. Proyecto de investigacion
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Proyecto de investigacion",
					"Evalua el proceso completo de un proyecto de investigacion escolar.",
					List.of(
							new DefaultCriterion("pregunta", "Pregunta de investigacion",
									"Formulacion, pertinencia y delimitacion.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin pregunta clara."),
											new DefaultDescriptor("EN_PROCESO", "Pregunta amplia o ambigua."),
											new DefaultDescriptor("ESPERADO", "Pregunta clara, delimitada y pertinente."),
											new DefaultDescriptor("SOBRESALIENTE", "Pregunta original y relevante para el campo."))),
							new DefaultCriterion("marco_teorico", "Marco teorico",
									"Revision bibliografica y conceptual.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin fuentes o fuentes no confiables."),
											new DefaultDescriptor("EN_PROCESO", "Pocas fuentes, sin organizacion."),
											new DefaultDescriptor("ESPERADO", "Suficientes fuentes organizadas."),
											new DefaultDescriptor("SOBRESALIENTE", "Amplia revision con fuentes primarias y secundarias."))),
							new DefaultCriterion("metodologia", "Metodologia",
									"Diseno, instrumentos y recoleccion.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin metodologia clara."),
											new DefaultDescriptor("EN_PROCESO", "Metodologia incompleta."),
											new DefaultDescriptor("ESPERADO", "Metodologia coherente y bien descrita."),
											new DefaultDescriptor("SOBRESALIENTE", "Metodologia rigurosa y replicable."))),
							new DefaultCriterion("analisis", "Analisis de resultados",
									"Interpretacion y discusion.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Describe sin analizar."),
											new DefaultDescriptor("EN_PROCESO", "Analisis superficial."),
											new DefaultDescriptor("ESPERADO", "Analisis consistente y bien fundamentado."),
											new DefaultDescriptor("SOBRESALIENTE", "Analisis critico y originales conexiones."))),
							new DefaultCriterion("presentacion", "Presentacion final",
									"Informe escrito y/o defensa oral.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Informe desordenado y confuso."),
											new DefaultDescriptor("EN_PROCESO", "Informe aceptable con errores."),
											new DefaultDescriptor("ESPERADO", "Informe bien estructurado y claro."),
											new DefaultDescriptor("SOBRESALIENTE", "Informe profesional con aporte personal.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 4. Examen de matematicas
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Examen de matematicas",
					"Evalua la resolucion de problemas y justificacion de procedimientos.",
					List.of(
							new DefaultCriterion("comprension", "Comprension del problema",
									"Identifica datos, incognitas y relaciones.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No identifica datos relevantes."),
											new DefaultDescriptor("EN_PROCESO", "Identifica parcialmente."),
											new DefaultDescriptor("ESPERADO", "Identifica todos los elementos."),
											new DefaultDescriptor("SOBRESALIENTE", "Identifica y reformula el problema."))),
							new DefaultCriterion("estrategia", "Estrategia de resolucion",
									"Seleccion y aplicacion de estrategias.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin estrategia clara."),
											new DefaultDescriptor("EN_PROCESO", "Estrategia incompleta."),
											new DefaultDescriptor("ESPERADO", "Estrategia adecuada y completa."),
											new DefaultDescriptor("SOBRESALIENTE", "Estrategia optima con alternativas."))),
							new DefaultCriterion("ejecucion", "Ejecucion",
									"Calculo y manipulacion algebraica.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Errores de calculo frecuentes."),
											new DefaultDescriptor("EN_PROCESO", "Errores ocasionales."),
											new DefaultDescriptor("ESPERADO", "Calculo correcto y prolijo."),
											new DefaultDescriptor("SOBRESALIENTE", "Calculo impecable con simplificaciones elegantes."))),
							new DefaultCriterion("justificacion", "Justificacion",
									"Argumentacion y validacion.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin justificacion."),
											new DefaultDescriptor("EN_PROCESO", "Justificacion incompleta."),
											new DefaultDescriptor("ESPERADO", "Justificacion clara y suficiente."),
											new DefaultDescriptor("SOBRESALIENTE", "Justificacion rigurosa con generalizaciones.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 5. Practica de laboratorio
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Practica de laboratorio",
					"Evalua el desempeno del estudiante en una practica experimental.",
					List.of(
							new DefaultCriterion("hipotesis", "Hipotesis",
									"Formulacion y fundamentacion.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin hipotesis."),
											new DefaultDescriptor("EN_PROCESO", "Hipotesis vaga."),
											new DefaultDescriptor("ESPERADO", "Hipotesis clara y testeable."),
											new DefaultDescriptor("SOBRESALIENTE", "Hipotesis original y bien fundamentada."))),
							new DefaultCriterion("procedimiento", "Procedimiento",
									"Manipulacion de materiales y seguridad.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Incumple normas de seguridad."),
											new DefaultDescriptor("EN_PROCESO", "Cumple normas con supervision."),
											new DefaultDescriptor("ESPERADO", "Trabaja con autonomia y seguridad."),
											new DefaultDescriptor("SOBRESALIENTE", "Domestica el procedimiento y propone mejoras."))),
							new DefaultCriterion("registro", "Registro de datos",
									"Precision y organizacion de datos.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Datos incompletos o ilegibles."),
											new DefaultDescriptor("EN_PROCESO", "Datos organizados con errores."),
											new DefaultDescriptor("ESPERADO", "Datos completos y bien organizados."),
											new DefaultDescriptor("SOBRESALIENTE", "Datos exhaustivos con unidades y rangos."))),
							new DefaultCriterion("analisis", "Analisis de resultados",
									"Interpretacion y conclusion.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin conclusion."),
											new DefaultDescriptor("EN_PROCESO", "Conclusion superficial."),
											new DefaultDescriptor("ESPERADO", "Conclusion respaldada por datos."),
											new DefaultDescriptor("SOBRESALIENTE", "Conclusion critica con fuentes teoricas."))),
							new DefaultCriterion("informe", "Informe de laboratorio",
									"Calidad del informe escrito.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Informe incompleto."),
											new DefaultDescriptor("EN_PROCESO", "Informe basico."),
											new DefaultDescriptor("ESPERADO", "Informe completo y claro."),
											new DefaultDescriptor("SOBRESALIENTE", "Informe profesional y bien fundamentado.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 6. Resolucion de problemas (general)
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Resolucion de problemas",
					"Rubrica transversal para evaluar resolucion de problemas en "
							+ "cualquier area (matematica, ciencias, sociales).",
					List.of(
							new DefaultCriterion("identificacion", "Identificacion del problema",
									"Reconoce el problema y sus elementos clave.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No identifica el problema."),
											new DefaultDescriptor("EN_PROCESO", "Identificacion parcial."),
											new DefaultDescriptor("ESPERADO", "Identifica todos los elementos clave."),
											new DefaultDescriptor("SOBRESALIENTE", "Reformula el problema y lo contextualiza."))),
							new DefaultCriterion("plan", "Planificacion",
									"Elabora un plan de accion.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin plan."),
											new DefaultDescriptor("EN_PROCESO", "Plan incompleto."),
											new DefaultDescriptor("ESPERADO", "Plan claro y logico."),
											new DefaultDescriptor("SOBRESALIENTE", "Plan detallado con alternativas."))),
							new DefaultCriterion("ejecucion", "Ejecucion del plan",
									"Lleva a cabo el plan con precision.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No ejecuta el plan."),
											new DefaultDescriptor("EN_PROCESO", "Ejecucion con errores."),
											new DefaultDescriptor("ESPERADO", "Ejecucion correcta."),
											new DefaultDescriptor("SOBRESALIENTE", "Ejecucion impecable y eficiente."))),
							new DefaultCriterion("revision", "Revision y reflexion",
									"Evalua el proceso y los resultados.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin revision."),
											new DefaultDescriptor("EN_PROCESO", "Revision superficial."),
											new DefaultDescriptor("ESPERADO", "Revisa y corrige lo necesario."),
											new DefaultDescriptor("SOBRESALIENTE", "Reflexiona y propone mejoras."))),
							new DefaultCriterion("comunicacion", "Comunicacion de resultados",
									"Presenta el resultado de forma clara.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Resultado incomprensible."),
											new DefaultDescriptor("EN_PROCESO", "Resultado confuso."),
											new DefaultDescriptor("ESPERADO", "Resultado claro."),
											new DefaultDescriptor("SOBRESALIENTE", "Comunicacion precisa y bien estructurada.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 7. Trabajo en equipo
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Trabajo en equipo",
					"Evalua la contribucion y desempeno del estudiante en un equipo.",
					List.of(
							new DefaultCriterion("participacion", "Participacion",
									"Aporte activo a las tareas del equipo.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No participa."),
											new DefaultDescriptor("EN_PROCESO", "Participa con insistencia."),
											new DefaultDescriptor("ESPERADO", "Participa activamente."),
											new DefaultDescriptor("SOBRESALIENTE", "Lidera y motiva al equipo."))),
							new DefaultCriterion("colaboracion", "Colaboracion",
									"Apoya a companeros y resuelve conflictos.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Obstaculiza el trabajo."),
											new DefaultDescriptor("EN_PROCESO", "Colabora con dificultad."),
											new DefaultDescriptor("ESPERADO", "Colabora efectivamente."),
											new DefaultDescriptor("SOBRESALIENTE", "Promueve un ambiente de confianza."))),
							new DefaultCriterion("responsabilidad", "Responsabilidad",
									"Cumple con sus tareas y plazos.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Incumple sistematicamente."),
											new DefaultDescriptor("EN_PROCESO", "Cumple parcialmente."),
											new DefaultDescriptor("ESPERADO", "Cumple con sus compromisos."),
											new DefaultDescriptor("SOBRESALIENTE", "Cumple y apoya a otros."))),
							new DefaultCriterion("calidad", "Calidad del aporte",
									"Calidad del trabajo entregado.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Aporte de baja calidad."),
											new DefaultDescriptor("EN_PROCESO", "Acepteable con errores."),
											new DefaultDescriptor("ESPERADO", "Buena calidad."),
											new DefaultDescriptor("SOBRESALIENTE", "Calidad sobresaliente."))),
							new DefaultCriterion("comunicacion", "Comunicacion",
									"Comunica ideas y escucha al equipo.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No comunica ni escucha."),
											new DefaultDescriptor("EN_PROCESO", "Se expresa con dificultad."),
											new DefaultDescriptor("ESPERADO", "Comunica con claridad."),
											new DefaultDescriptor("SOBRESALIENTE", "Facilita la comunicacion del equipo.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 8. Lectura comprensiva
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Lectura comprensiva",
					"Evalua el nivel de comprension lectora del estudiante.",
					List.of(
							new DefaultCriterion("literal", "Nivel literal",
									"Identifica informacion explicita del texto.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No identifica informacion literal."),
											new DefaultDescriptor("EN_PROCESO", "Identifica parcialmente."),
											new DefaultDescriptor("ESPERADO", "Identifica la informacion literal completa."),
											new DefaultDescriptor("SOBRESALIENTE", "Identifica y cita con precision."))),
							new DefaultCriterion("inferencial", "Nivel inferencial",
									"Infiere informacion no explicita.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No realiza inferencias."),
											new DefaultDescriptor("EN_PROCESO", "Realiza inferencias basicas."),
											new DefaultDescriptor("ESPERADO", "Infiere relaciones y propositos."),
											new DefaultDescriptor("SOBRESALIENTE", "Realiza inferencias complejas y originales."))),
							new DefaultCriterion("critico", "Nivel critico",
									"Evalua y valora el texto.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "No emite juicio critico."),
											new DefaultDescriptor("EN_PROCESO", "Juicio superficial."),
											new DefaultDescriptor("ESPERADO", "Evalua con argumentos."),
											new DefaultDescriptor("SOBRESALIENTE", "Argumenta con criterio propio y solido."))),
							new DefaultCriterion("coherencia", "Coherencia interpretativa",
									"Construye una interpretacion global.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin interpretacion global."),
											new DefaultDescriptor("EN_PROCESO", "Interpretacion fragmentada."),
											new DefaultDescriptor("ESPERADO", "Interpretacion coherente."),
											new DefaultDescriptor("SOBRESALIENTE", "Interpretacion integra y profunda.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 9. Produccion escrita
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Produccion escrita",
					"Evalua la escritura de textos en distintos formatos.",
					List.of(
							new DefaultCriterion("adecuacion", "Adecuacion",
									"Registro, tono y formato segun el destinatario.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Inadecuado al contexto."),
											new DefaultDescriptor("EN_PROCESO", "Adecuacion basica."),
											new DefaultDescriptor("ESPERADO", "Adecuado al contexto."),
											new DefaultDescriptor("SOBRESALIENTE", "Excelente manejo del registro."))),
							new DefaultCriterion("cohesion", "Cohesion",
									"Conexion entre oraciones y parrafos.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin cohesion."),
											new DefaultDescriptor("EN_PROCESO", "Cohesion debil."),
											new DefaultDescriptor("ESPERADO", "Cohesion clara."),
											new DefaultDescriptor("SOBRESALIENTE", "Cohesion fluida y variada."))),
							new DefaultCriterion("coherencia", "Coherencia",
									"Logica global y progresion tematica.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin coherencia."),
											new DefaultDescriptor("EN_PROCESO", "Coherencia parcial."),
											new DefaultDescriptor("ESPERADO", "Coherente y bien organizado."),
											new DefaultDescriptor("SOBRESALIENTE", "Coherencia solida con argumento central."))),
							new DefaultCriterion("vocabulario", "Vocabulario",
									"Riqueza y precision lexica.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Vocabulario limitado."),
											new DefaultDescriptor("EN_PROCESO", "Vocabulario basico."),
											new DefaultDescriptor("ESPERADO", "Vocabulario variado y preciso."),
											new DefaultDescriptor("SOBRESALIENTE", "Vocabulario amplio, preciso y estilistico."))),
							new DefaultCriterion("ortografia", "Ortografia y gramatica",
									"Cumplimiento de normas.",
									java.math.BigDecimal.valueOf(15),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Errores frecuentes."),
											new DefaultDescriptor("EN_PROCESO", "Errores aislados."),
											new DefaultDescriptor("ESPERADO", "Errores minimos."),
											new DefaultDescriptor("SOBRESALIENTE", "Texto impecable.")))
					),
					canonicalLevels()
			),

			// ----------------------------------------------------------------
			// 10. Mapa conceptual
			// ----------------------------------------------------------------
			new DefaultRubric(
					"Mapa conceptual",
					"Evalua la elaboracion de mapas conceptuales como herramienta "
							+ "de organizacion del conocimiento.",
					List.of(
							new DefaultCriterion("conceptos", "Seleccion de conceptos",
									"Pertinencia y exhaustividad.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Conceptos irrelevantes."),
											new DefaultDescriptor("EN_PROCESO", "Conceptos parciales."),
											new DefaultDescriptor("ESPERADO", "Conceptos pertinentes y suficientes."),
											new DefaultDescriptor("SOBRESALIENTE", "Conceptos amplios y bien jerarquizados."))),
							new DefaultCriterion("relaciones", "Relaciones entre conceptos",
									"Precision de las conexiones.",
									java.math.BigDecimal.valueOf(30),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Relaciones incorrectas."),
											new DefaultDescriptor("EN_PROCESO", "Relaciones basicas."),
											new DefaultDescriptor("ESPERADO", "Relaciones claras y logicas."),
											new DefaultDescriptor("SOBRESALIENTE", "Relaciones complejas y bien etiquetadas."))),
							new DefaultCriterion("jerarquia", "Jerarquia",
									"Organizacion piramidal.",
									java.math.BigDecimal.valueOf(20),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Sin jerarquia."),
											new DefaultDescriptor("EN_PROCESO", "Jerarquia basica."),
											new DefaultDescriptor("ESPERADO", "Jerarquia clara."),
											new DefaultDescriptor("SOBRESALIENTE", "Jerarquia optima con niveles multiples."))),
							new DefaultCriterion("presentacion", "Presentacion visual",
									"Claridad y limpieza.",
									java.math.BigDecimal.valueOf(25),
									List.of(
											new DefaultDescriptor("EN_INICIO", "Ilegible."),
											new DefaultDescriptor("EN_PROCESO", "Presentacion aceptable."),
											new DefaultDescriptor("ESPERADO", "Presentacion clara y prolija."),
											new DefaultDescriptor("SOBRESALIENTE", "Presentacion profesional.")))
					),
					canonicalLevels()
			)
	);

	private static List<DefaultLevel> canonicalLevels() {
		return List.of(
				new DefaultLevel("EN_INICIO", "En inicio", 1),
				new DefaultLevel("EN_PROCESO", "En proceso", 2),
				new DefaultLevel("ESPERADO", "Esperado", 3),
				new DefaultLevel("SOBRESALIENTE", "Sobresaliente", 4)
		);
	}

	/**
	 * Snapshot of the level order to keep the seed file readable. The
	 * canonical codes match {@code RubricLevel} enum names; tenants
	 * can fork and use a subset (2..4) of these.
	 */
	public static final Map<String, String> LEVEL_DISPLAY_NAMES = Map.of(
			"EN_INICIO", "En inicio",
			"EN_PROCESO", "En proceso",
			"ESPERADO", "Esperado",
			"SOBRESALIENTE", "Sobresaliente"
	);
}
