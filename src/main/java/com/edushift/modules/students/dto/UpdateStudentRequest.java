package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

/**
 * Partial-update payload for {@code PUT /v1/students/{publicUuid}}.
 *
 * <p><strong>null = no change</strong>; the service merges field by
 * field. Blank-string semantics:
 * <ul>
 *   <li>nullable fields ({@code email}, {@code phone}, {@code address},
 *       {@code secondLastName}) are cleared when the patch carries a
 *       blank string. Admins need a way to remove a phone or wipe a
 *       wrong address.</li>
 *   <li>required fields keep their non-blank constraint via the size /
 *       pattern annotations — sending a blank value is a 400.</li>
 * </ul>
 */
public record UpdateStudentRequest(
		DocumentType documentType,

		@Size(min = 4, max = 20, message = "documentNumber length out of range")
		@Pattern(regexp = "^[A-Za-z0-9-]+$",
				message = "documentNumber must contain only letters, digits, and dashes")
		String documentNumber,

		@Size(min = 1, max = 100, message = "firstName length out of range")
		String firstName,

		@Size(min = 1, max = 100, message = "lastName length out of range")
		String lastName,

		@Size(max = 100, message = "secondLastName must be at most 100 characters")
		String secondLastName,

		LocalDate birthDate,

		Gender gender,

		@Email(message = "email must be a valid address")
		@Size(max = 254, message = "email must be at most 254 characters")
		String email,

		@Size(max = 32, message = "phone must be at most 32 characters")
		String phone,

		@Size(max = 500, message = "address must be at most 500 characters")
		String address,

		EnrollmentStatus enrollmentStatus,

		LocalDate enrollmentDate,

		Map<String, Object> metadata
) {
	public boolean isEmpty() {
		return documentType == null
				&& documentNumber == null
				&& firstName == null
				&& lastName == null
				&& secondLastName == null
				&& birthDate == null
				&& gender == null
				&& email == null
				&& phone == null
				&& address == null
				&& enrollmentStatus == null
				&& enrollmentDate == null
				&& metadata == null;
	}
}
