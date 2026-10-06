package com.edushift.modules.teachers.bulk;

import com.edushift.modules.students.service.bulk.BulkImportHeaderAliases;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spanish display labels + aliases for teacher bulk-import headers.
 * Shared person fields reuse {@link BulkImportHeaderAliases}; teacher-only
 * fields ({@code title}, {@code specializations}, {@code hireDate},
 * {@code employmentStatus}) live here.
 */
public final class TeacherBulkImportHeaderAliases {

	public static final String TITLE = "title";
	public static final String SPECIALIZATIONS = "specializations";
	public static final String HIRE_DATE = "hireDate";
	public static final String EMPLOYMENT_STATUS = "employmentStatus";

	private static final Map<String, String> DISPLAY_ES = buildDisplayEs();
	private static final Map<String, String> ALIAS_TO_CANONICAL = buildAliasMap();

	private TeacherBulkImportHeaderAliases() {
	}

	public static List<String> required() {
		return List.of(
				BulkImportHeaderAliases.DOCUMENT_TYPE,
				BulkImportHeaderAliases.DOCUMENT_NUMBER,
				BulkImportHeaderAliases.FIRST_NAME,
				BulkImportHeaderAliases.LAST_NAME);
	}

	/** Ordered canonical → Spanish label for the official template. */
	public static Map<String, String> templateColumnsEs() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(DISPLAY_ES));
	}

	public static String displayLabelEs(String canonical) {
		return DISPLAY_ES.getOrDefault(canonical, canonical);
	}

	/** All canonical teacher fields in template order. */
	public static List<String> allCanonical() {
		return List.copyOf(DISPLAY_ES.keySet());
	}

	/**
	 * Suggests {@code canonical → exact header} using the first matching
	 * column for each canonical field (left-to-right).
	 */
	public static Map<String, String> suggestMapping(List<String> exactHeaders) {
		Map<String, String> suggested = new LinkedHashMap<>();
		for (String header : exactHeaders) {
			String canonical = resolveCanonical(header);
			if (canonical == null) {
				continue;
			}
			suggested.putIfAbsent(canonical, header);
		}
		return suggested;
	}

	public static List<String> unmappedRequired(Map<String, String> mapping) {
		List<String> missing = new java.util.ArrayList<>();
		for (String required : required()) {
			String header = mapping == null ? null : mapping.get(required);
			if (header == null || header.isBlank()) {
				missing.add(required);
			}
		}
		return missing;
	}

	/**
	 * @return canonical field key, or {@code null} if unknown
	 */
	public static String resolveCanonical(String header) {
		String fromShared = BulkImportHeaderAliases.resolveCanonical(header);
		if (fromShared != null
				&& !fromShared.startsWith("guardian")
				&& !fromShared.equals(BulkImportHeaderAliases.ADDRESS)
				&& !fromShared.equals(BulkImportHeaderAliases.ENROLLMENT_STATUS)
				&& !fromShared.equals(BulkImportHeaderAliases.ENROLLMENT_DATE)
				&& !fromShared.equals(BulkImportHeaderAliases.FULL_NAME)) {
			return fromShared;
		}
		String key = BulkImportHeaderAliases.normalize(header);
		if (key.isEmpty()) {
			return null;
		}
		return ALIAS_TO_CANONICAL.get(key);
	}

	private static Map<String, String> buildDisplayEs() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put(BulkImportHeaderAliases.DOCUMENT_TYPE, "Tipo de documento");
		m.put(BulkImportHeaderAliases.DOCUMENT_NUMBER, "Número de documento");
		m.put(BulkImportHeaderAliases.FIRST_NAME, "Nombres");
		m.put(BulkImportHeaderAliases.LAST_NAME, "Apellido paterno");
		m.put(BulkImportHeaderAliases.SECOND_LAST_NAME, "Apellido materno");
		m.put(BulkImportHeaderAliases.BIRTH_DATE, "Fecha de nacimiento");
		m.put(BulkImportHeaderAliases.GENDER, "Género");
		m.put(BulkImportHeaderAliases.EMAIL, "Correo");
		m.put(BulkImportHeaderAliases.PHONE, "Teléfono");
		m.put(TITLE, "Título");
		m.put(SPECIALIZATIONS, "Especialidades");
		m.put(HIRE_DATE, "Fecha de contratación");
		m.put(EMPLOYMENT_STATUS, "Estado laboral");
		return m;
	}

	private static Map<String, String> buildAliasMap() {
		Map<String, String> m = new LinkedHashMap<>();
		alias(m, TITLE, "title", "titulo", "tituloprofesional");
		alias(m, SPECIALIZATIONS,
				"specializations", "especialidades", "especialidad", "materias");
		alias(m, HIRE_DATE,
				"hiredate", "fechacontratacion", "fechadecontratacion",
				"fechaingreso", "fechadeingreso");
		alias(m, EMPLOYMENT_STATUS,
				"employmentstatus", "estadolaboral", "estadoempleo", "estado");

		for (Map.Entry<String, String> e : buildDisplayEs().entrySet()) {
			m.put(BulkImportHeaderAliases.normalize(e.getValue()), e.getKey());
			m.put(BulkImportHeaderAliases.normalize(e.getKey()), e.getKey());
		}
		return Map.copyOf(m);
	}

	private static void alias(Map<String, String> m, String canonical, String... aliases) {
		m.put(BulkImportHeaderAliases.normalize(canonical), canonical);
		for (String a : aliases) {
			m.put(BulkImportHeaderAliases.normalize(a), canonical);
		}
	}
}
