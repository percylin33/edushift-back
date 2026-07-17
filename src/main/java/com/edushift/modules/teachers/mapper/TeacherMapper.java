package com.edushift.modules.teachers.mapper;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.TeacherListItem;
import com.edushift.modules.teachers.dto.TeacherResponse;
import com.edushift.modules.teachers.dto.UpdateTeacherRequest;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Teacher}. Same convention as
 * {@code StudentMapper}.
 *
 * <p>The {@code userId} on the entity is internal-only — the response
 * surfaces the linked user's {@code publicUuid} instead, looked up
 * from {@link UserRepository}. The lookup is a single-row read, kept
 * here so the service layer doesn't have to babysit projections.</p>
 */
@Component
@RequiredArgsConstructor
public class TeacherMapper {

	private final UserRepository userRepository;

	public TeacherResponse toResponse(Teacher teacher) {
		return new TeacherResponse(
				teacher.getPublicUuid(),
				teacher.getDocumentType(),
				teacher.getDocumentNumber(),
				teacher.getFirstName(),
				teacher.getLastName(),
				teacher.getSecondLastName(),
				teacher.getBirthDate(),
				teacher.getGender(),
				teacher.getEmail(),
				teacher.getPhone(),
				teacher.getTitle(),
				safeList(teacher.getSpecializations()),
				teacher.getHireDate(),
				teacher.getEmploymentStatus(),
				resolveUserPublicUuid(teacher),
				safeMap(teacher.getMetadata()),
				teacher.getCreatedAt(),
				teacher.getUpdatedAt()
		);
	}

	public TeacherListItem toListItem(Teacher teacher) {
		return new TeacherListItem(
				teacher.getPublicUuid(),
				teacher.getDocumentType(),
				teacher.getDocumentNumber(),
				teacher.getFirstName(),
				teacher.getLastName(),
				teacher.getSecondLastName(),
				teacher.getEmail(),
				teacher.getTitle(),
				safeList(teacher.getSpecializations()),
				teacher.getEmploymentStatus(),
				teacher.getUserId() != null);
	}

	public Teacher fromCreate(CreateTeacherRequest request) {
		Teacher teacher = new Teacher();
		teacher.setDocumentType(request.documentType());
		teacher.setDocumentNumber(request.documentNumber().trim());
		teacher.setFirstName(request.firstName().trim());
		teacher.setLastName(request.lastName().trim());
		teacher.setSecondLastName(blankToNull(request.secondLastName()));
		teacher.setBirthDate(request.birthDate());
		teacher.setGender(request.gender() == null ? Gender.NOT_SPECIFIED : request.gender());
		teacher.setEmail(blankToNull(request.email()));
		teacher.setPhone(blankToNull(request.phone()));
		teacher.setTitle(blankToNull(request.title()));
		teacher.setSpecializations(safeList(request.specializations()));
		teacher.setHireDate(request.hireDate());
		teacher.setEmploymentStatus(request.employmentStatus() == null
				? EmploymentStatus.ACTIVE : request.employmentStatus());
		teacher.setMetadata(safeMap(request.metadata()));
		return teacher;
	}

	public void applyUpdate(UpdateTeacherRequest patch, Teacher teacher) {
		if (patch.documentType() != null) teacher.setDocumentType(patch.documentType());
		if (patch.documentNumber() != null) teacher.setDocumentNumber(patch.documentNumber().trim());
		if (patch.firstName() != null) teacher.setFirstName(patch.firstName().trim());
		if (patch.lastName() != null) teacher.setLastName(patch.lastName().trim());
		if (patch.secondLastName() != null) {
			teacher.setSecondLastName(blankToNull(patch.secondLastName()));
		}
		if (patch.birthDate() != null) teacher.setBirthDate(patch.birthDate());
		if (patch.gender() != null) teacher.setGender(patch.gender());
		if (patch.email() != null) teacher.setEmail(blankToNull(patch.email()));
		if (patch.phone() != null) teacher.setPhone(blankToNull(patch.phone()));
		if (patch.title() != null) teacher.setTitle(blankToNull(patch.title()));
		if (patch.specializations() != null) {
			teacher.setSpecializations(new ArrayList<>(patch.specializations()));
		}
		if (patch.hireDate() != null) teacher.setHireDate(patch.hireDate());
		if (patch.employmentStatus() != null) {
			teacher.setEmploymentStatus(patch.employmentStatus());
		}
		if (patch.metadata() != null) {
			teacher.setMetadata(new HashMap<>(patch.metadata()));
		}
	}

	private java.util.UUID resolveUserPublicUuid(Teacher teacher) {
		if (teacher.getUserId() == null) return null;
		// DEBT-FK-BUGS-3 / V77: teachers.user_id stores users.public_uuid
		// (not users.id). The column is now a direct publicUuid — no
		// translation needed, but we verify the value still resolves to
		// a real user (defensive against stale rows from before V77).
		return userRepository.findByPublicUuid(teacher.getUserId())
				.map(User::getPublicUuid)
				.orElse(null);
	}

	private static List<String> safeList(List<String> raw) {
		return raw == null ? List.of() : List.copyOf(raw);
	}

	private static Map<String, Object> safeMap(Map<String, Object> raw) {
		return raw == null ? new HashMap<>() : new HashMap<>(raw);
	}

	private static String blankToNull(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
