package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves Spanish / synonym cell values onto enum constants used by
 * the student bulk-import parser.
 */
public final class BulkImportEnumAliases {

	private static final Map<String, Gender> GENDER = Map.ofEntries(
			Map.entry("MALE", Gender.MALE),
			Map.entry("MASCULINO", Gender.MALE),
			Map.entry("HOMBRE", Gender.MALE),
			Map.entry("FEMALE", Gender.FEMALE),
			Map.entry("FEMENINO", Gender.FEMALE),
			Map.entry("MUJER", Gender.FEMALE),
			Map.entry("OTHER", Gender.OTHER),
			Map.entry("OTRO", Gender.OTHER),
			Map.entry("NOTSPECIFIED", Gender.NOT_SPECIFIED),
			Map.entry("NOSPECIFICADO", Gender.NOT_SPECIFIED),
			Map.entry("NOESPECIFICADO", Gender.NOT_SPECIFIED));

	private static final Map<String, EmploymentStatus> EMPLOYMENT = Map.ofEntries(
			Map.entry("ACTIVE", EmploymentStatus.ACTIVE),
			Map.entry("ACTIVO", EmploymentStatus.ACTIVE),
			Map.entry("ONLEAVE", EmploymentStatus.ON_LEAVE),
			Map.entry("LICENCIA", EmploymentStatus.ON_LEAVE),
			Map.entry("PERMISO", EmploymentStatus.ON_LEAVE),
			Map.entry("RESIGNED", EmploymentStatus.RESIGNED),
			Map.entry("RENUNCIADO", EmploymentStatus.RESIGNED),
			Map.entry("RETIRED", EmploymentStatus.RETIRED),
			Map.entry("JUBILADO", EmploymentStatus.RETIRED),
			Map.entry("SUSPENDED", EmploymentStatus.SUSPENDED),
			Map.entry("SUSPENDIDO", EmploymentStatus.SUSPENDED));

	private static final Map<String, RelationshipType> RELATIONSHIP = Map.ofEntries(
			Map.entry("FATHER", RelationshipType.FATHER),
			Map.entry("PADRE", RelationshipType.FATHER),
			Map.entry("MOTHER", RelationshipType.MOTHER),
			Map.entry("MADRE", RelationshipType.MOTHER),
			Map.entry("GRANDPARENT", RelationshipType.GRANDPARENT),
			Map.entry("ABUELO", RelationshipType.GRANDPARENT),
			Map.entry("ABUELA", RelationshipType.GRANDPARENT),
			Map.entry("ABUELOS", RelationshipType.GRANDPARENT),
			Map.entry("GUARDIAN", RelationshipType.GUARDIAN),
			Map.entry("APODERADO", RelationshipType.GUARDIAN),
			Map.entry("TUTOR", RelationshipType.GUARDIAN),
			Map.entry("OTHER", RelationshipType.OTHER),
			Map.entry("OTRO", RelationshipType.OTHER));

	private static final Map<String, EnrollmentStatus> ENROLLMENT = Map.ofEntries(
			Map.entry("PENDING", EnrollmentStatus.PENDING),
			Map.entry("PENDIENTE", EnrollmentStatus.PENDING),
			Map.entry("ENROLLED", EnrollmentStatus.ENROLLED),
			Map.entry("MATRICULADO", EnrollmentStatus.ENROLLED),
			Map.entry("GRADUATED", EnrollmentStatus.GRADUATED),
			Map.entry("EGRESADO", EnrollmentStatus.GRADUATED),
			Map.entry("TRANSFERRED", EnrollmentStatus.TRANSFERRED),
			Map.entry("TRANSFERIDO", EnrollmentStatus.TRANSFERRED),
			Map.entry("WITHDRAWN", EnrollmentStatus.WITHDRAWN),
			Map.entry("RETIRADO", EnrollmentStatus.WITHDRAWN));

	private BulkImportEnumAliases() {
	}

	public static Gender parseGender(String raw) {
		return lookup(GENDER, raw);
	}

	public static RelationshipType parseRelationship(String raw) {
		return lookup(RELATIONSHIP, raw);
	}

	public static EnrollmentStatus parseEnrollmentStatus(String raw) {
		return lookup(ENROLLMENT, raw);
	}

	public static EmploymentStatus parseEmploymentStatus(String raw) {
		return lookup(EMPLOYMENT, raw);
	}

	public static DocumentType parseDocumentType(String raw) {
		String key = key(raw);
		if (key == null) {
			return null;
		}
		try {
			return DocumentType.valueOf(key);
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static <E> E lookup(Map<String, E> map, String raw) {
		String k = key(raw);
		if (k == null) {
			return null;
		}
		return map.get(k);
	}

	private static String key(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = BulkImportHeaderAliases.normalize(raw);
		return normalized.isEmpty() ? null : normalized.toUpperCase(Locale.ROOT);
	}
}
