package com.edushift.modules.disclosures.dto;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Source of truth for the initial annual disclosure checklist. */
public final class DisclosureCatalog {
	private static final List<DisclosureFieldDefinition> FIELDS = List.of(
			field("internal_regulation", "Documentos", "Reglamento interno", "Adjunta la versión que estará vigente este año. Ejemplo: “Reglamento interno 2027, aprobado el 15/11/2026”."),
			field("school_owner", "Identificación del colegio", "Promotor o propietario", "Indica el nombre del promotor o la razón social y su tipo de identificación, según corresponda. Ejemplo: “Asociación Educativa Los Pinos, RUC …”."),
			field("school_director", "Identificación del colegio", "Director del colegio", "Escribe el nombre completo y el cargo de quien dirige el colegio. Ejemplo: “María Pérez — Directora”."),
			field("school_authorizations", "Identificación del colegio", "Resoluciones y autorizaciones del colegio", "Sube el documento oficial vigente que autoriza el servicio educativo o el local. Ejemplo: resolución de autorización o ampliación. Este campo se responde solo con archivo."),
			field("enrollment_fee", "Información económica", "Cuota de matrícula", "EduShift trae los conceptos y montos activos de matrícula desde Pagos. Ejemplo: “Matrícula Primaria: S/ 350”. Revisa que los nombres distingan los niveles si tienen tarifas diferentes."),
			field("monthly_tuition", "Información económica", "Pensiones", "EduShift trae los montos de los planes automáticos por nivel o grado. Ejemplo: “Primaria: S/ 480 al mes”. Configura los importes en Pagos > Planes automáticos."),
			field("tuition_payment_dates", "Información económica", "Fechas de pago", "EduShift trae el día y la frecuencia configurados para cada plan de pensiones. Ejemplo: “Primaria: vence el día 5 de cada mes”."),
			field("fee_increases", "Información económica", "Posibles aumentos", "Explica si se prevé un cambio, cuándo se comunicaría y qué criterio se usaría. Ejemplo: “No se prevén aumentos para este año” o “cualquier cambio se informará antes de su aplicación”."),
			field("tuition_history", "Información económica", "Historial de los últimos cinco años", "EduShift acumula los montos del año y de las publicaciones anuales anteriores. Ejemplo: “2026: matrícula S/ 350; pensión S/ 480”. No registres aquí los años anteriores."),
			field("admission_fee", "Información económica", "Cuota de ingreso", "Indica el monto y cómo/cuándo se paga, o explica que no se cobra. Ejemplo: “S/ 1,000, pago único al confirmar la vacante” o “El colegio no cobra cuota de ingreso”."),
			field("admission_fee_refund", "Información económica", "Devolución de cuota de ingreso", "Describe cuándo corresponde una devolución y cómo solicitarla, si aplica. Ejemplo: “La familia presenta una solicitud en Secretaría y recibe respuesta por correo”. Si no aplica, indícalo expresamente."),
			field("certificate_retention", "Información económica", "Retención de certificados", "Explica si el colegio retiene certificados y bajo qué política, o indica que no lo hace. Ejemplo: “El colegio no retiene certificados por pagos pendientes”."),
			field("admission_requirements", "Admisión y vacantes", "Requisitos de admisión", "Enumera los documentos y condiciones que se solicitan. Ejemplo: “DNI del estudiante y apoderado, ficha de inscripción y libreta de notas”. Incluye solo requisitos vigentes."),
			field("admission_process", "Admisión y vacantes", "Proceso y plazos de admisión", "Resume los pasos, fechas o plazos y cómo hacer consultas. Ejemplo: “1. Completar la solicitud; 2. Entrevista; 3. Respuesta en cinco días hábiles. Consultas: Secretaría”."),
			field("vacancies", "Admisión y vacantes", "Vacantes", "EduShift muestra las vacantes calculadas por grado cuando Admisiones y los aforos están configurados. Ejemplo: “1.º de Primaria: 8 vacantes”. Revisa los datos en Admisiones."),
			field("curriculum_plan", "Propuesta educativa", "Plan curricular", "Adjunta el plan del año o resume los aprendizajes y áreas principales por nivel. Ejemplo: “En 1.º de Secundaria se dictan Comunicación, Matemática, Ciencia y Tecnología…”."),
			field("methodology", "Propuesta educativa", "Metodología", "Cuenta cómo se desarrollan las clases y actividades. Ejemplo: “Proyectos en equipo, experimentos guiados y lectura semanal”. Menciona solo prácticas que use el colegio."),
			field("pedagogical_system", "Propuesta educativa", "Sistema pedagógico", "Describe el enfoque educativo y cómo se organiza la enseñanza. Ejemplo: “Enfoque por competencias con acompañamiento tutorial y actividades diferenciadas por nivel”."),
			field("evaluation_system", "Propuesta educativa", "Sistema de evaluación", "Explica qué se evalúa, cómo se informa el avance y cómo puede la familia consultarlo. Ejemplo: “Evaluaciones, trabajos y proyectos; los avances se reportan en el portal familiar al cierre de cada periodo”."),
			field("attendance_system", "Propuesta educativa", "Control de asistencia", "Indica cómo se registra la asistencia y cómo se avisa a las familias. Ejemplo: “La asistencia se registra al inicio de la jornada y Secretaría notifica las inasistencias por el portal”."),
			field("school_calendar", "Calendario y servicio", "Calendario escolar", "EduShift lo trae del año académico y sus periodos configurados. Ejemplo: “Inicio: 10/03; receso: 28/07 al 08/08; cierre: 19/12”. Revisa las fechas del año seleccionado."),
			field("school_schedule", "Calendario y servicio", "Jornada y horarios", "EduShift muestra automáticamente la jornada configurada por nivel, grado y turno, con recreos y almuerzos cuando están registrados. Configúrala en Jornada y horarios."),
			field("max_students_per_class", "Calendario y servicio", "Máximo de estudiantes por aula", "EduShift trae el aforo configurado en cada sección. Ejemplo: “1.º A: máximo 25 estudiantes”. Este límite es pedagógico y no corresponde al límite del plan de EduShift."),
			field("support_services", "Servicios", "Servicios de apoyo", "Lista los servicios complementarios y sus condiciones o costos, si los tienen. Ejemplo: “Tópico durante la jornada, sin costo adicional; movilidad escolar, tarifa según ruta”. Si no ofrecen servicios, indícalo."));
	private static final Map<String, DisclosureFieldDefinition> BY_KEY = FIELDS.stream()
			.collect(Collectors.toUnmodifiableMap(DisclosureFieldDefinition::key, Function.identity()));

	private DisclosureCatalog() { }

	public static List<DisclosureFieldDefinition> fields() { return FIELDS; }
	public static Map<String, DisclosureFieldDefinition> byKey() { return BY_KEY; }
	private static DisclosureFieldDefinition field(String key, String section, String label, String guidance) {
		return new DisclosureFieldDefinition(key, section, label, guidance);
	}
}
