package com.edushift.modules.students.provision;

import java.util.Map;

/**
 * CRED-7 printable family handout copy stored under
 * {@code tenants.settings.credentialHandout}.
 */
public record CredentialHandoutSettings(
		String welcomeTitle,
		String welcomeBody,
		String loginUrlOverride,
		String supportPhone,
		String supportNote
) {
	public static final String SETTINGS_KEY = "credentialHandout";

	public static CredentialHandoutSettings defaults() {
		return new CredentialHandoutSettings(
				"Acceso a la plataforma del colegio",
				"EduShift es el portal del colegio: notas, tareas, asistencia y comunicados. "
						+ "Ingresa con los datos de abajo, cambia la contraseña temporal "
						+ "y anota la nueva en el recuadro.",
				null,
				null,
				"Si olvidaste tu clave y no tienes correo real, acude a secretaría "
						+ "con tu DNI para que te reemitan el acceso."
		);
	}

	@SuppressWarnings("unchecked")
	public static CredentialHandoutSettings fromTenantSettings(Map<String, Object> settings) {
		CredentialHandoutSettings d = defaults();
		if (settings == null || settings.isEmpty()) {
			return d;
		}
		Object raw = settings.get(SETTINGS_KEY);
		if (!(raw instanceof Map<?, ?> map)) {
			return d;
		}
		Map<String, Object> node = (Map<String, Object>) map;
		return new CredentialHandoutSettings(
				str(node.get("welcomeTitle"), d.welcomeTitle()),
				str(node.get("welcomeBody"), d.welcomeBody()),
				blankToNull(str(node.get("loginUrlOverride"), null)),
				blankToNull(str(node.get("supportPhone"), null)),
				str(node.get("supportNote"), d.supportNote())
		);
	}

	private static String str(Object value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? fallback : s;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
