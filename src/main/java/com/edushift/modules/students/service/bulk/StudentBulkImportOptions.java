package com.edushift.modules.students.service.bulk;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime options for a student bulk-import job (not persisted).
 *
 * @param columnMapping          canonical field → exact Excel header; {@code null}
 *                               falls back to alias resolution
 * @param sectionPublicUuid      optional destination section for every OK row
 * @param academicYearPublicUuid required when {@code sectionPublicUuid} is set
 * @param enrolledAt             enrollment date; defaulted by the service when null
 */
public record StudentBulkImportOptions(
		Map<String, String> columnMapping,
		UUID sectionPublicUuid,
		UUID academicYearPublicUuid,
		LocalDate enrolledAt
) {
	public static StudentBulkImportOptions none() {
		return new StudentBulkImportOptions(null, null, null, null);
	}

	public boolean hasSection() {
		return sectionPublicUuid != null;
	}
}
