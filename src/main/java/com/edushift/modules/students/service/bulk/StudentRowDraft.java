package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import java.time.LocalDate;

/**
 * One spreadsheet row, parsed into typed fields, ready for validation
 * and persistence by {@code StudentBulkImportRunner}.
 *
 * @param rowNumber 1-based index as it appears in the spreadsheet
 *                  (header = 1, first data row = 2)
 */
public record StudentRowDraft(
		int rowNumber,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String secondLastName,
		LocalDate birthDate,
		Gender gender,
		String email,
		String phone,
		String address,
		EnrollmentStatus enrollmentStatus,
		LocalDate enrollmentDate,
		DocumentType guardianDocumentType,
		String guardianDocumentNumber,
		String guardianFirstName,
		String guardianLastName,
		String guardianPhone,
		String guardianEmail,
		RelationshipType guardianRelationship
) {
	public boolean hasAnyGuardianField() {
		return guardianDocumentType != null
				|| notBlank(guardianDocumentNumber)
				|| notBlank(guardianFirstName)
				|| notBlank(guardianLastName)
				|| notBlank(guardianPhone)
				|| notBlank(guardianEmail)
				|| guardianRelationship != null;
	}

	public boolean hasCompleteGuardian() {
		return guardianDocumentType != null
				&& notBlank(guardianDocumentNumber)
				&& notBlank(guardianFirstName)
				&& notBlank(guardianLastName);
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
