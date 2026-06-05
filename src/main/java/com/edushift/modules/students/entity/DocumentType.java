package com.edushift.modules.students.entity;

/**
 * Identity-document kind for a {@link Student}.
 *
 * <h3>Local context</h3>
 * The set is shaped around Peruvian / Latin-American institutions
 * (DNI = Documento Nacional de Identidad, CE = Carnet de Extranjería)
 * with {@code PASSPORT} for foreign students and {@code OTHER} as the
 * escape hatch for outliers (institutional cards, cédula, etc.). The
 * enum is intentionally small — anything beyond these four belongs in
 * the free-form {@code metadata} jsonb, not as a new enum value.
 */
public enum DocumentType {
	DNI,
	CE,
	PASSPORT,
	OTHER;

	public static DocumentType fromName(String name) {
		if (name == null) return null;
		for (DocumentType d : values()) {
			if (d.name().equals(name)) return d;
		}
		return null;
	}
}
