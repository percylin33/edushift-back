package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionSource;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Applicant details for inquiry conversion or direct presential admission. */
public record CreateAdmissionFromInquiryRequest(
		@NotNull AdmissionSource source,
		@NotNull DocumentType studentDocumentType,
		@NotBlank @Size(min = 4, max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$") String studentDocumentNumber,
		@NotBlank @Size(max = 100) String studentFirstName,
		@NotBlank @Size(max = 100) String studentLastName,
		@Size(max = 100) String studentSecondLastName,
		@NotNull LocalDate studentBirthDate,
		@NotNull Gender studentGender,
		@NotNull DocumentType guardianDocumentType,
		@NotBlank @Size(min = 4, max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$") String guardianDocumentNumber,
		@NotBlank @Size(max = 100) String guardianFirstName,
		@NotBlank @Size(max = 100) String guardianLastName,
		@Email @Size(max = 254) String guardianEmail,
		@NotBlank @Size(max = 32) String guardianPhone,
		@NotNull RelationshipType guardianRelationship,
		@Size(max = 2000) String note,
		UUID academicYearPublicUuid,
		UUID gradePublicUuid
) {}
