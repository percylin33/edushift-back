package com.edushift.modules.teachers.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Payload of {@code POST /v1/teachers}.
 *
 * <p>{@code email} and {@code phone} are optional; institutions often
 * carry old hires whose contact details were not digitised. {@code
 * specializations} is a small free-form jsonb array — institutions
 * tag teachers with subjects ({@code "Matematica"}, {@code "Fisica"})
 * for the FE filter UX.</p>
 */
public record CreateTeacherRequest(

		@NotNull(message = "documentType is required")
		DocumentType documentType,

		@NotBlank(message = "documentNumber is required")
		@Size(min = 1, max = 20, message = "documentNumber length out of range")
		String documentNumber,

		@NotBlank(message = "firstName is required")
		@Size(min = 1, max = 100, message = "firstName length out of range")
		String firstName,

		@NotBlank(message = "lastName is required")
		@Size(min = 1, max = 100, message = "lastName length out of range")
		String lastName,

		@Size(max = 100, message = "secondLastName too long")
		String secondLastName,

		LocalDate birthDate,

		Gender gender,

		@Email(message = "email must be a valid address")
		@Size(max = 254, message = "email too long")
		String email,

		@Size(max = 32, message = "phone too long")
		@Pattern(regexp = "^[+0-9\\s\\-()]{6,32}$",
				message = "phone must contain only digits, spaces, dashes, parentheses, and an optional leading +")
		String phone,

		@Size(max = 50, message = "title too long")
		String title,

		List<@Size(min = 1, max = 100,
				message = "specialization items must be 1..100 chars") String> specializations,

		LocalDate hireDate,

		EmploymentStatus employmentStatus,

		Map<String, Object> metadata
) {
}
