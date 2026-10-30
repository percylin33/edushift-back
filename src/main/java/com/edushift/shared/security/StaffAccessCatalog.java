package com.edushift.shared.security;

import java.util.List;
import java.util.Set;

/** Built-in, composable permission bundles available to school staff. */
public final class StaffAccessCatalog {

	private StaffAccessCatalog() {}

	public enum ProfileCode {
		SECRETARIA,
		ADMISIONES,
		ASISTENCIA,
		FINANZAS,
		COORDINACION,
		SOPORTE,
		INFORMACION_ANUAL,
		LEGACY_OPERATIONS
	}

	public record ProfileOption(
			ProfileCode code,
			String label,
			String description,
			boolean legacy,
			Set<String> authorities) {}

	public record PermissionOption(
			String authority,
			String label,
			String category,
			boolean sensitive) {}

	private static final List<ProfileOption> PROFILES = List.of(
			new ProfileOption(ProfileCode.SECRETARIA, "Secretaría",
					"Alumnos, apoderados, matrículas y atención administrativa.", false,
					Set.of(LmsAuthorities.STUDENTS_READ, LmsAuthorities.STUDENTS_CREATE,
							LmsAuthorities.STUDENTS_UPDATE, LmsAuthorities.GUARDIANS_READ,
							LmsAuthorities.GUARDIANS_MANAGE, LmsAuthorities.ENROLLMENTS_READ,
							LmsAuthorities.ENROLLMENTS_MANAGE,
							LmsAuthorities.ATTENDANCE_READ, LmsAuthorities.USERS_PROVISION_SECTION,
							LmsAuthorities.USERS_PROVISION_STUDENT, LmsAuthorities.USERS_PROVISION_GUARDIAN)),
			new ProfileOption(ProfileCode.ADMISIONES, "Admisiones",
					"Postulantes, expedientes y matrícula inicial.", false,
					Set.of(LmsAuthorities.STUDENTS_READ, LmsAuthorities.STUDENTS_CREATE,
							LmsAuthorities.STUDENTS_UPDATE, LmsAuthorities.GUARDIANS_READ,
							LmsAuthorities.GUARDIANS_MANAGE, LmsAuthorities.ENROLLMENTS_READ,
							LmsAuthorities.ENROLLMENTS_MANAGE,
							LmsAuthorities.ADMISSIONS_INQUIRIES_READ,
							LmsAuthorities.ADMISSIONS_INQUIRIES_MANAGE,
							LmsAuthorities.ADMISSIONS_APPLICATIONS_READ,
							LmsAuthorities.ADMISSIONS_APPLICATIONS_MANAGE,
							LmsAuthorities.ADMISSIONS_DECIDE,
							LmsAuthorities.ADMISSIONS_OFFERS_MANAGE,
							LmsAuthorities.ADMISSIONS_ENROLL,
							LmsAuthorities.USERS_PROVISION_STUDENT, LmsAuthorities.USERS_PROVISION_GUARDIAN)),
			new ProfileOption(ProfileCode.ASISTENCIA, "Asistencia",
					"Consultar y registrar asistencia; corregir registros según las reglas del colegio.", false,
					Set.of(LmsAuthorities.STUDENTS_READ, LmsAuthorities.ATTENDANCE_READ,
							LmsAuthorities.ATTENDANCE_REGISTER, LmsAuthorities.ATTENDANCE_CORRECT)),
			new ProfileOption(ProfileCode.FINANZAS, "Finanzas",
					"Consultar, emitir cuotas, recibir y conciliar pagos. No incluye reembolsos ni conceptos.", false,
					Set.of(LmsAuthorities.PAYMENTS_READ,
							LmsAuthorities.PAYMENTS_RECEIVE, LmsAuthorities.PAYMENTS_RECONCILE,
							LmsAuthorities.PAYMENTS_ISSUE)),
			new ProfileOption(ProfileCode.COORDINACION, "Coordinación académica",
					"Consultar alumnos, matrículas y asistencia.", false,
					Set.of(LmsAuthorities.STUDENTS_READ, LmsAuthorities.ENROLLMENTS_READ,
							LmsAuthorities.ATTENDANCE_READ)),
			new ProfileOption(ProfileCode.SOPORTE, "Recepción y soporte",
					"Ayudar con el acceso al portal de alumnos y apoderados por sección.", false,
					Set.of(LmsAuthorities.USERS_PROVISION_SECTION)),
			new ProfileOption(ProfileCode.INFORMACION_ANUAL, "Información anual",
					"Preparar y enviar información anual a revisión.", false,
					Set.of(LmsAuthorities.LMS_DISCLOSURE_READ,
							LmsAuthorities.LMS_DISCLOSURE_MANAGE,
							LmsAuthorities.LMS_DISCLOSURE_SUBMIT)),
			new ProfileOption(ProfileCode.LEGACY_OPERATIONS, "Acceso anterior (revisar)",
					"Conserva los permisos de STAFF que existían antes de esta actualización.", true,
					Set.of(LmsAuthorities.LMS_TASK_READ, LmsAuthorities.LMS_MATERIAL_READ,
							LmsAuthorities.LMS_QUIZ_READ, LmsAuthorities.LMS_PAYMENT_ADMIN,
							LmsAuthorities.LMS_DISCLOSURE_READ, LmsAuthorities.LMS_DISCLOSURE_MANAGE,
							LmsAuthorities.LMS_DISCLOSURE_SUBMIT, LmsAuthorities.USERS_PROVISION_SECTION))
	);

	private static final List<PermissionOption> PERMISSIONS = List.of(
			permission(LmsAuthorities.STUDENTS_READ, "Consultar alumnos", "Alumnos", false),
			permission(LmsAuthorities.STUDENTS_CREATE, "Crear alumnos", "Alumnos", false),
			permission(LmsAuthorities.STUDENTS_UPDATE, "Editar alumnos", "Alumnos", false),
			permission(LmsAuthorities.USERS_PROVISION_STUDENT, "Preparar acceso de alumnos", "Cuentas", false),
			permission(LmsAuthorities.GUARDIANS_READ, "Consultar apoderados", "Apoderados", false),
			permission(LmsAuthorities.GUARDIANS_MANAGE, "Gestionar apoderados", "Apoderados", false),
			permission(LmsAuthorities.USERS_PROVISION_GUARDIAN, "Preparar acceso de apoderados", "Cuentas", false),
			permission(LmsAuthorities.ENROLLMENTS_READ, "Consultar matrículas", "Matrículas", false),
			permission(LmsAuthorities.ENROLLMENTS_MANAGE, "Gestionar matrículas", "Matrículas", false),
			permission(LmsAuthorities.ATTENDANCE_READ, "Consultar asistencia", "Asistencia", false),
			permission(LmsAuthorities.ATTENDANCE_REGISTER, "Registrar asistencia", "Asistencia", false),
			permission(LmsAuthorities.ATTENDANCE_CORRECT, "Corregir asistencia", "Asistencia", true),
			permission(LmsAuthorities.PAYMENTS_READ, "Consultar pagos", "Finanzas", false),
			permission(LmsAuthorities.PAYMENTS_RECEIVE, "Registrar pagos recibidos", "Finanzas", true),
			permission(LmsAuthorities.PAYMENTS_RECONCILE, "Conciliar pagos", "Finanzas", true),
			permission(LmsAuthorities.PAYMENTS_ISSUE, "Emitir cuotas individuales y masivas", "Finanzas", true),
			permission(LmsAuthorities.PAYMENTS_REFUND, "Reembolsar pagos", "Finanzas", true),
			permission(LmsAuthorities.PAYMENTS_EXPORT, "Exportar reportes de pagos", "Finanzas", true),
			permission(LmsAuthorities.PAYMENT_CONCEPTS_MANAGE, "Administrar conceptos de pago", "Finanzas", true),
			permission(LmsAuthorities.LMS_ADMISSION_MANAGE, "Gestionar admisiones", "Admisiones", false),
			permission(LmsAuthorities.ADMISSIONS_INQUIRIES_READ, "Consultar consultas de familias", "Admisiones", false),
			permission(LmsAuthorities.ADMISSIONS_INQUIRIES_MANAGE, "Contactar y calificar consultas", "Admisiones", false),
			permission(LmsAuthorities.ADMISSIONS_APPLICATIONS_READ, "Consultar postulaciones y vacantes", "Admisiones", false),
			permission(LmsAuthorities.ADMISSIONS_APPLICATIONS_MANAGE, "Formalizar postulaciones", "Admisiones", true),
			permission(LmsAuthorities.ADMISSIONS_DECIDE, "Resolver postulaciones", "Admisiones", true),
			permission(LmsAuthorities.ADMISSIONS_OFFERS_MANAGE, "Enviar y renovar ofertas familiares", "Admisiones", true),
			permission(LmsAuthorities.ADMISSIONS_ENROLL, "Crear matrícula desde una oferta confirmada", "Admisiones", true),
			permission(LmsAuthorities.USERS_PROVISION_SECTION, "Preparar cuentas por sección", "Cuentas", true),
			permission(LmsAuthorities.LMS_TASK_READ, "Consultar tareas", "Aula virtual", false),
			permission(LmsAuthorities.LMS_MATERIAL_READ, "Consultar materiales", "Aula virtual", false),
			permission(LmsAuthorities.LMS_QUIZ_READ, "Consultar cuestionarios", "Aula virtual", false),
			permission(LmsAuthorities.LMS_DISCLOSURE_READ, "Consultar información anual", "Información anual", false),
			permission(LmsAuthorities.LMS_DISCLOSURE_MANAGE, "Preparar información anual", "Información anual", false),
			permission(LmsAuthorities.LMS_DISCLOSURE_SUBMIT, "Enviar información anual a revisión", "Información anual", false),
			permission(LmsAuthorities.LMS_PAYMENT_ADMIN, "Administrar pagos (acceso anterior)", "Finanzas", true)
	);

	private static PermissionOption permission(
			String authority, String label, String category, boolean sensitive) {
		return new PermissionOption(authority, label, category, sensitive);
	}

	public static List<ProfileOption> profiles() {
		return PROFILES;
	}

	public static List<PermissionOption> permissions() {
		return PERMISSIONS;
	}

	public static Set<String> authoritiesFor(ProfileCode code) {
		return PROFILES.stream()
				.filter(profile -> profile.code() == code)
				.findFirst()
				.map(ProfileOption::authorities)
				.orElse(Set.of());
	}

	public static boolean supportsAuthority(String authority) {
		return PERMISSIONS.stream().anyMatch(permission -> permission.authority().equals(authority));
	}
}
