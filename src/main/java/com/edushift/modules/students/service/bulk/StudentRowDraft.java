package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import java.time.LocalDate;

/**
 * One spreadsheet row, parsed into typed fields, ready for validation
 * and persistence by {@code StudentBulkImportRunner}.
 *
 * <p>This record lives at the parsing boundary on purpose — it is not
 * a DTO that round-trips through the API, so it doesn't carry
 * validation annotations. Validation happens by hand inside the
 * runner so the per-row error messages can be tailored to the
 * spreadsheet workflow ({@code "Row 7: documentType is required"})
 * instead of the generic Bean Validation output.
 *
 * @param rowNumber 1-based index as it appears in the spreadsheet
 *                  (header = 1, first data row = 2). The runner uses
 *                  this when recording per-row errors so admins can
 *                  navigate to the offending row.
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
		LocalDate enrollmentDate
) {
}
