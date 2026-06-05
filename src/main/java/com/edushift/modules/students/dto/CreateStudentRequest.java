package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

/**
 * Body of {@code POST /v1/students}.
 *
 * <h3>Required vs optional</h3>
 * Required: {@code documentType + documentNumber} (the natural identity)
 * and the basic name pair. Everything else (email, phone, address,
 * birthDate, secondLastName, enrollmentDate, metadata, status) is
 * optional and assumes safe defaults at the entity level.
 */
public record CreateStudentRequest(
		@NotNull(message = "documentType is required")
		DocumentType documentType,

		@NotBlank(message = "documentNumber is required")
		@Size(min = 4, max = 20, message = "documentNumber length out of range")
		@Pattern(regexp = "^[A-Za-z0-9-]+$",
				message = "documentNumber must contain only letters, digits, and dashes")
		String documentNumber,

		@NotBlank(message = "firstName is required")
		@Size(min = 1, max = 100, message = "firstName length out of range")
		String firstName,

		@NotBlank(message = "lastName is required")
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
}
