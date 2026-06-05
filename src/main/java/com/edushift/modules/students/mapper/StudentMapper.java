package com.edushift.modules.students.mapper;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Student;
import java.util.HashMap;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between the {@link Student} aggregate and its DTOs.
 *
 * <h3>Why hand-written</h3>
 * Same reasoning as {@code TenantMapper} and {@code UserManagementMapper}:
 * the partial-merge semantics ({@code null} = no change, blank = clear
 * for nullable fields) and the metadata jsonb deserve to be pinned in
 * code and tests. MapStruct conventions would either fight the contract
 * or hide it behind a custom mapping method anyway.
 */
@Component
public class StudentMapper {

	// ===========================================================================
	// Read side
	// ===========================================================================

	public StudentListItem toListItem(Student s) {
		return new StudentListItem(
				s.getPublicUuid(),
				s.getDocumentType(),
				s.getDocumentNumber(),
				s.getFirstName(),
				s.getLastName(),
				s.fullName(),
				s.getEmail(),
				s.getEnrollmentStatus(),
				s.getEnrollmentDate()
		);
	}

	public StudentResponse toResponse(Student s) {
		return new StudentResponse(
				s.getPublicUuid(),
				s.getDocumentType(),
				s.getDocumentNumber(),
				s.getFirstName(),
				s.getLastName(),
				s.getSecondLastName(),
				s.fullName(),
				s.getBirthDate(),
				s.getGender(),
				s.getEmail(),
				s.getPhone(),
				s.getAddress(),
				s.getEnrollmentStatus(),
				s.getEnrollmentDate(),
				s.getUserId(),
				// Defensive copy: callers must not be able to mutate the
				// entity's metadata via the response.
				new HashMap<>(s.getMetadata()),
				s.getCreatedAt(),
				s.getUpdatedAt()
		);
	}

	// ===========================================================================
	// Write side
	// ===========================================================================

	/**
	 * Materialises a brand-new entity from a create request. The service
	 * is responsible for normalising / validating before calling this.
	 */
	public Student fromCreate(CreateStudentRequest request) {
		Student s = new Student();
		s.setDocumentType(request.documentType());
		s.setDocumentNumber(request.documentNumber().trim());
		s.setFirstName(request.firstName().trim());
		s.setLastName(request.lastName().trim());
		s.setSecondLastName(trimOrNull(request.secondLastName()));
		s.setBirthDate(request.birthDate());
		s.setGender(request.gender() == null ? Gender.NOT_SPECIFIED : request.gender());
		s.setEmail(trimOrNull(request.email()));   // entity normalises lowercase on persist
		s.setPhone(trimOrNull(request.phone()));
		s.setAddress(trimOrNull(request.address()));
		s.setEnrollmentStatus(request.enrollmentStatus() == null
				? EnrollmentStatus.PENDING : request.enrollmentStatus());
		s.setEnrollmentDate(request.enrollmentDate());
		s.setMetadata(request.metadata() == null ? new HashMap<>() : new HashMap<>(request.metadata()));
		return s;
	}

	/**
	 * Applies a partial patch in place. Mirrors the convention from
	 * {@code UserManagementMapper}: null = no change, blank string on a
	 * nullable column = clear. {@code metadata} replaces wholesale when
	 * the patch carries a non-null value (a partial-merge semantics on
	 * jsonb belongs in a higher-level "extension manager" — not here).
	 */
	public void applyUpdate(UpdateStudentRequest patch, Student s) {
		if (patch == null || patch.isEmpty()) return;

		if (patch.documentType() != null) {
			s.setDocumentType(patch.documentType());
		}
		if (patch.documentNumber() != null) {
			s.setDocumentNumber(patch.documentNumber().trim());
		}
		if (patch.firstName() != null) {
			s.setFirstName(patch.firstName().trim());
		}
		if (patch.lastName() != null) {
			s.setLastName(patch.lastName().trim());
		}
		if (patch.secondLastName() != null) {
			s.setSecondLastName(blankToNull(patch.secondLastName()));
		}
		if (patch.birthDate() != null) {
			s.setBirthDate(patch.birthDate());
		}
		if (patch.gender() != null) {
			s.setGender(patch.gender());
		}
		if (patch.email() != null) {
			s.setEmail(blankToNull(patch.email()));
		}
		if (patch.phone() != null) {
			s.setPhone(blankToNull(patch.phone()));
		}
		if (patch.address() != null) {
			s.setAddress(blankToNull(patch.address()));
		}
		if (patch.enrollmentStatus() != null) {
			s.setEnrollmentStatus(patch.enrollmentStatus());
		}
		if (patch.enrollmentDate() != null) {
			s.setEnrollmentDate(patch.enrollmentDate());
		}
		if (patch.metadata() != null) {
			// Wholesale replace — see class-level Javadoc.
			s.setMetadata(new HashMap<>(patch.metadata()));
		}
	}

	private static String trimOrNull(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String blankToNull(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
