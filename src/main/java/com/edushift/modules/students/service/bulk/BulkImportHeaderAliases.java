package com.edushift.modules.students.service.bulk;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps spreadsheet header labels (EN camelCase, ES human labels, or
 * near-synonyms) onto the canonical bulk-import field keys.
 *
 * <p>Normalisation strips diacritics and non-alphanumerics so
 * {@code "Tipo de documento"}, {@code "tipoDocumento"} and
 * {@code "documentType"} all resolve to the same canonical field.
 */
public final class BulkImportHeaderAliases {

	public static final String DOCUMENT_TYPE = "documentType";
	public static final String DOCUMENT_NUMBER = "documentNumber";
	public static final String FIRST_NAME = "firstName";
	public static final String LAST_NAME = "lastName";
	public static final String SECOND_LAST_NAME = "secondLastName";
	/** Single column with full person name; parser splits into first/last. */
	public static final String FULL_NAME = "fullName";
	public static final String BIRTH_DATE = "birthDate";
	public static final String GENDER = "gender";
	public static final String EMAIL = "email";
	public static final String PHONE = "phone";
	public static final String ADDRESS = "address";
	public static final String ENROLLMENT_STATUS = "enrollmentStatus";
	public static final String ENROLLMENT_DATE = "enrollmentDate";

	public static final String GUARDIAN_DOCUMENT_TYPE = "guardianDocumentType";
	public static final String GUARDIAN_DOCUMENT_NUMBER = "guardianDocumentNumber";
	public static final String GUARDIAN_FIRST_NAME = "guardianFirstName";
	public static final String GUARDIAN_LAST_NAME = "guardianLastName";
	public static final String GUARDIAN_FULL_NAME = "guardianFullName";
	public static final String GUARDIAN_PHONE = "guardianPhone";
	public static final String GUARDIAN_EMAIL = "guardianEmail";
	public static final String GUARDIAN_RELATIONSHIP = "guardianRelationship";

	private static final Map<String, String> ALIAS_TO_CANONICAL = buildAliasMap();
	private static final Map<String, String> DISPLAY_ES = buildDisplayEs();

	private BulkImportHeaderAliases() {
	}

	public static List<String> studentRequired() {
		return List.of(DOCUMENT_TYPE, DOCUMENT_NUMBER, FIRST_NAME, LAST_NAME);
	}

	public static List<String> guardianRequired() {
		return List.of(
				GUARDIAN_DOCUMENT_TYPE,
				GUARDIAN_DOCUMENT_NUMBER,
				GUARDIAN_FIRST_NAME,
				GUARDIAN_LAST_NAME);
	}

	public static List<String> allCanonical() {
		return List.copyOf(DISPLAY_ES.keySet());
	}

	public static String displayLabelEs(String canonical) {
		return DISPLAY_ES.getOrDefault(canonical, canonical);
	}

	/** Ordered display labels for the official Spanish template. */
	public static Map<String, String> templateColumnsEs() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(DISPLAY_ES));
	}

	/**
	 * Columns for the downloadable .xlsx. Omits {@link #FULL_NAME} /
	 * {@link #GUARDIAN_FULL_NAME} so the sample row stays aligned with
	 * first+last name fields (those full-name aliases still work on upload).
	 */
	public static Map<String, String> templateColumnsEsForDownload() {
		Map<String, String> m = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : DISPLAY_ES.entrySet()) {
			if (FULL_NAME.equals(e.getKey()) || GUARDIAN_FULL_NAME.equals(e.getKey())) {
				continue;
			}
			m.put(e.getKey(), e.getValue());
		}
		return Collections.unmodifiableMap(m);
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String nfd = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD);
		StringBuilder sb = new StringBuilder(nfd.length());
		for (int i = 0; i < nfd.length(); i++) {
			char c = nfd.charAt(i);
			if (Character.getType(c) == Character.NON_SPACING_MARK) {
				continue;
			}
			if (Character.isLetterOrDigit(c)) {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	/**
	 * @return canonical field key, or {@code null} if the header is unknown
	 */
	public static String resolveCanonical(String header) {
		String key = normalize(header);
		if (key.isEmpty()) {
			return null;
		}
		return ALIAS_TO_CANONICAL.get(key);
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
		List<String> missing = new ArrayList<>();
		boolean hasFullName = mapping != null
				&& mapping.get(FULL_NAME) != null
				&& !mapping.get(FULL_NAME).isBlank();
		for (String required : studentRequired()) {
			if ((required.equals(FIRST_NAME) || required.equals(LAST_NAME)) && hasFullName) {
				continue;
			}
			String header = mapping == null ? null : mapping.get(required);
			if (header == null || header.isBlank()) {
				missing.add(required);
			}
		}
		return missing;
	}

	/**
	 * Required guardian fields missing from an explicit column mapping.
	 * {@link #GUARDIAN_FULL_NAME} satisfies first+last the same way
	 * {@link #FULL_NAME} does for students.
	 */
	public static List<String> unmappedGuardianRequired(Map<String, String> mapping) {
		List<String> missing = new ArrayList<>();
		boolean hasFullName = mapping != null
				&& mapping.get(GUARDIAN_FULL_NAME) != null
				&& !mapping.get(GUARDIAN_FULL_NAME).isBlank();
		for (String required : guardianRequired()) {
			if ((required.equals(GUARDIAN_FIRST_NAME) || required.equals(GUARDIAN_LAST_NAME))
					&& hasFullName) {
				continue;
			}
			String header = mapping == null ? null : mapping.get(required);
			if (header == null || header.isBlank()) {
				missing.add(required);
			}
		}
		return missing;
	}

	/** True when any guardian-related column is present in the mapping. */
	public static boolean hasAnyGuardianMapping(Map<String, String> mapping) {
		if (mapping == null || mapping.isEmpty()) {
			return false;
		}
		for (String required : guardianRequired()) {
			String v = mapping.get(required);
			if (v != null && !v.isBlank()) {
				return true;
			}
		}
		return (mapping.containsKey(GUARDIAN_FULL_NAME)
				&& mapping.get(GUARDIAN_FULL_NAME) != null
				&& !mapping.get(GUARDIAN_FULL_NAME).isBlank())
				|| mapping.containsKey(GUARDIAN_PHONE)
				|| mapping.containsKey(GUARDIAN_EMAIL)
				|| mapping.containsKey(GUARDIAN_RELATIONSHIP);
	}

	private static Map<String, String> buildDisplayEs() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put(DOCUMENT_TYPE, "Tipo de documento");
		m.put(DOCUMENT_NUMBER, "Número de documento");
		m.put(FIRST_NAME, "Nombres");
		m.put(LAST_NAME, "Apellido paterno");
		m.put(SECOND_LAST_NAME, "Apellido materno");
		m.put(FULL_NAME, "Nombre completo");
		m.put(BIRTH_DATE, "Fecha de nacimiento");
		m.put(GENDER, "Género");
		m.put(EMAIL, "Correo");
		m.put(PHONE, "Teléfono");
		m.put(ADDRESS, "Dirección");
		m.put(ENROLLMENT_STATUS, "Estado de matrícula");
		m.put(ENROLLMENT_DATE, "Fecha de matrícula");
		m.put(GUARDIAN_DOCUMENT_TYPE, "Tipo doc. apoderado");
		m.put(GUARDIAN_DOCUMENT_NUMBER, "Número doc. apoderado");
		m.put(GUARDIAN_FIRST_NAME, "Nombres apoderado");
		m.put(GUARDIAN_LAST_NAME, "Apellido apoderado");
		m.put(GUARDIAN_FULL_NAME, "Nombre completo apoderado");
		m.put(GUARDIAN_PHONE, "Teléfono apoderado");
		m.put(GUARDIAN_EMAIL, "Correo apoderado");
		m.put(GUARDIAN_RELATIONSHIP, "Parentesco");
		return m;
	}

	private static Map<String, String> buildAliasMap() {
		Map<String, String> m = new LinkedHashMap<>();
		alias(m, DOCUMENT_TYPE, "documenttype", "tipodocumento", "tipodedocumento",
				"tipodedocestudiante", "tipodocestudiante", "tipodedocumentoestudiante");
		alias(m, DOCUMENT_NUMBER,
				"documentnumber", "numerodocumento", "nrodocumento",
				"numerodedocumento", "dni",
				"ndocestudiante", "nrodocestudiante", "numerodocestudiante",
				"numerodedocumentoestudiante");
		alias(m, FIRST_NAME, "firstname", "nombres", "nombre");
		alias(m, LAST_NAME,
				"lastname", "apellidopaterno", "primerapellido", "apellido", "apellidos");
		alias(m, SECOND_LAST_NAME,
				"secondlastname", "apellidomaterno", "segundoapellido");
		alias(m, FULL_NAME,
				"fullname", "nombrecompleto", "alumno", "estudiante",
				"nombrecompletoestudiante");
		alias(m, BIRTH_DATE,
				"birthdate", "fechanacimiento", "fechadenacimiento", "nacimiento");
		alias(m, GENDER, "gender", "genero", "sexo");
		alias(m, EMAIL, "email", "correo", "correoelectronico");
		alias(m, PHONE, "phone", "telefono", "celular", "movil");
		alias(m, ADDRESS, "address", "direccion");
		alias(m, ENROLLMENT_STATUS,
				"enrollmentstatus", "estadomatricula", "estadodematrícula",
				"estadoenrollment");
		alias(m, ENROLLMENT_DATE,
				"enrollmentdate", "fechamatricula", "fechadematricula");

		alias(m, GUARDIAN_DOCUMENT_TYPE,
				"guardiandocumenttype", "tipodocapoderado", "tipodocumentoapoderado",
				"tipodedocumentoapoderado", "tipodedocapoderado");
		alias(m, GUARDIAN_DOCUMENT_NUMBER,
				"guardiandocumentnumber", "numerodocapoderado", "numerodocumentoapoderado",
				"dniapoderado", "nrodocapoderado", "ndocapoderado");
		alias(m, GUARDIAN_FIRST_NAME,
				"guardianfirstname", "nombresapoderado", "nombreapoderado");
		alias(m, GUARDIAN_LAST_NAME,
				"guardianlastname", "apellidoapoderado", "apellidosapoderado");
		alias(m, GUARDIAN_FULL_NAME,
				"guardianfullname", "nombrecompletoapoderado", "nombrecompletodelapoderado",
				"apoderado");
		alias(m, GUARDIAN_PHONE,
				"guardianphone", "telefonoapoderado", "celularapoderado", "movilapoderado",
				"telefonodelapoderado");
		alias(m, GUARDIAN_EMAIL,
				"guardianemail", "correoapoderado", "emailapoderado");
		alias(m, GUARDIAN_RELATIONSHIP,
				"guardianrelationship", "parentesco", "relacionapoderado");

		// Also accept the Spanish display labels via normalize().
		for (Map.Entry<String, String> e : buildDisplayEs().entrySet()) {
			m.put(normalize(e.getValue()), e.getKey());
			m.put(normalize(e.getKey()), e.getKey());
		}
		return Map.copyOf(m);
	}

	private static void alias(Map<String, String> m, String canonical, String... aliases) {
		m.put(normalize(canonical), canonical);
		for (String a : aliases) {
			m.put(normalize(a), canonical);
		}
	}
}
