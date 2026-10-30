package com.edushift.modules.sessions.template.entity;

/**
 * Organizational family for session templates (catalog grouping).
 */
public enum SessionTemplateFamily {
	STANDARD,
	COMMUNICATION,
	MATH,
	CUSTOM;

	public String labelEs() {
		return switch (this) {
			case STANDARD -> "Estándar MINEDU";
			case COMMUNICATION -> "Comunicación / Lectura";
			case MATH -> "Matemática";
			case CUSTOM -> "Propias del colegio";
		};
	}

	public static SessionTemplateFamily fromTemplateKey(String templateKey) {
		if (templateKey == null || templateKey.isBlank()) {
			return CUSTOM;
		}
		String k = templateKey.toLowerCase();
		if (k.startsWith("minedu-standard")) {
			return STANDARD;
		}
		if (k.startsWith("minedu-abc")) {
			return COMMUNICATION;
		}
		if (k.startsWith("minedu-math")) {
			return MATH;
		}
		return CUSTOM;
	}
}
