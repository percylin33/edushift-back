package com.edushift.modules.students.service.impl;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.InviteStudentResponse;
import com.edushift.modules.students.dto.LinkStudentUserRequest;
import com.edushift.modules.students.dto.StudentListFilters;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.mapper.StudentMapper;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.service.StudentService;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.UserInvitation;
import com.edushift.modules.users.repository.UserInvitationRepository;
import com.edushift.modules.users.service.UserInvitationService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link StudentService}.
 *
 * <h3>Tenant scoping</h3>
 * Every {@link StudentRepository} call goes through Hibernate's
 * {@code @TenantId} discriminator, so cross-tenant access naturally
 * surfaces as a {@code 404 RESOURCE_NOT_FOUND} (the row exists but is
 * filtered out from the caller's view).
 *
 * <h3>Filter composition</h3>
 * Filters live in {@link Specs} and compose as {@code AND}. The list
 * endpoint passes through the {@code gradeLevelId} field for
 * forward-compatibility (Sprint 4) but the service ignores it for now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

	private final StudentRepository studentRepository;
	private final StudentMapper mapper;
	private final UserRepository userRepository;
	private final UserInvitationService invitationService;
	private final UserInvitationRepository invitationRepository;

	/** Convention key carried in {@code user_invitations.metadata}. */
	public static final String METADATA_STUDENT_ID_KEY = "studentId";

	// ===========================================================================
	// Reads
	// ===========================================================================

	@Override
	@Transactional(readOnly = true)
	public Page<StudentListItem> listStudents(StudentListFilters filters, Pageable pageable) {
		StudentListFilters effective = filters == null ? StudentListFilters.empty() : filters;

		if (effective.gradeLevelId() != null && !effective.gradeLevelId().isBlank()) {
			// Sprint 4 lands the gradeLevel association; until then we
			// log + ignore so the contract stays forward-compatible.
			log.debug("[students] gradeLevelId filter received but not yet honoured (Sprint 4): {}",
					effective.gradeLevelId());
		}

		Specification<Student> spec = Specs.combine(effective);
		return studentRepository.findAll(spec, pageable).map(mapper::toListItem);
	}

	@Override
	@Transactional(readOnly = true)
	public StudentResponse getStudent(UUID publicUuid) {
		Student student = loadStudent(publicUuid);
		return mapper.toResponse(student, pendingInvitationUuid(student));
	}

	// ===========================================================================
	// Writes
	// ===========================================================================

	@Override
	@Transactional
	public StudentResponse createStudent(CreateStudentRequest request) {
		// Pre-check uniqueness so callers get a clean conflict code
		// instead of a generic DataIntegrityViolation. The DB partial
		// unique indexes (V10) remain the belt-and-suspenders guarantee
		// against TOCTOU races; we map them in the catch block below.
		ensureDocumentAvailable(request.documentType(), request.documentNumber(), null);
		ensureEmailAvailable(normalisedEmail(request.email()), null);

		Student student = mapper.fromCreate(request);

		try {
			Student saved = studentRepository.saveAndFlush(student);
			log.info("[students] created -- publicUuid={} document={}/{}",
					saved.getPublicUuid(), saved.getDocumentType(), saved.getDocumentNumber());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw mapUniqueViolation(ex, request.documentType(),
					request.documentNumber(), request.email());
		}
	}

	@Override
	@Transactional
	public StudentResponse updateStudent(UUID publicUuid, UpdateStudentRequest request) {
		Student student = loadStudent(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(student);
		}

		// Re-validate uniqueness only when the offending columns are
		// actually being changed. Excluding the entity itself avoids
		// the "patch with the same value" false positive.
		DocumentType targetDocType = request.documentType() != null
				? request.documentType() : student.getDocumentType();
		String targetDocNumber = request.documentNumber() != null
				? request.documentNumber().trim() : student.getDocumentNumber();
		boolean docChanged = !targetDocType.equals(student.getDocumentType())
				|| !targetDocNumber.equals(student.getDocumentNumber());
		if (docChanged) {
			ensureDocumentAvailable(targetDocType, targetDocNumber, student.getId());
		}

		if (request.email() != null) {
			String targetEmail = normalisedEmail(request.email());
			boolean emailChanged = !equalsIgnoreCaseSafe(targetEmail, student.getEmail());
			if (emailChanged && targetEmail != null) {
				ensureEmailAvailable(targetEmail, student.getId());
			}
		}

		mapper.applyUpdate(request, student);

		try {
			Student saved = studentRepository.saveAndFlush(student);
			log.info("[students] updated -- publicUuid={} document={}/{}",
					saved.getPublicUuid(), saved.getDocumentType(), saved.getDocumentNumber());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw mapUniqueViolation(ex, targetDocType, targetDocNumber, request.email());
		}
	}

	@Override
	@Transactional
	public void deleteStudent(UUID publicUuid) {
		Student student = loadStudent(publicUuid);
		studentRepository.delete(student);   // @SQLDelete soft-deletes
		log.info("[students] deleted -- publicUuid={} document={}/{}",
				student.getPublicUuid(), student.getDocumentType(), student.getDocumentNumber());
	}

	@Override
	@Transactional
	public StudentResponse linkUser(UUID publicUuid, LinkStudentUserRequest request) {
		Student student = loadStudent(publicUuid);
		if (student.getUserId() != null) {
			throw new ConflictException("STUDENT_ALREADY_HAS_USER",
					"Student '" + student.fullName() + "' is already linked to a user");
		}

		User user = userRepository.findByPublicUuid(request.userPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException("User", request.userPublicUuid()));

		if (!user.hasRole(UserRole.STUDENT)) {
			throw new ConflictException("USER_NOT_STUDENT_ROLE",
					"Cannot link user '" + user.getEmail() + "': missing STUDENT role");
		}

		studentRepository.findByUserId(user.getPublicUuid()).ifPresent(other -> {
			throw new ConflictException("USER_ALREADY_LINKED_TO_STUDENT",
					"User '" + user.getEmail() + "' is already linked to another student");
		});

		student.setUserId(user.getPublicUuid());
		Student saved = studentRepository.saveAndFlush(student);
		log.info("[students] link-user -- student={} user={}",
				saved.getPublicUuid(), user.getPublicUuid());
		return mapper.toResponse(saved, null);
	}

	@Override
	@Transactional
	public InviteStudentResponse invite(UUID publicUuid) {
		Student student = loadStudent(publicUuid);

		if (student.getUserId() != null) {
			throw new ConflictException("STUDENT_ALREADY_HAS_USER",
					"Student '" + student.fullName() + "' already has a linked user");
		}
		if (student.getEmail() == null || student.getEmail().isBlank()) {
			throw new BusinessException("STUDENT_EMAIL_REQUIRED",
					"Student '" + student.fullName() + "' needs an email to be invited");
		}

		Map<String, Object> metadata = new HashMap<>();
		metadata.put(METADATA_STUDENT_ID_KEY, student.getId().toString());

		InvitationResponse response = invitationService.createInvitation(
				new CreateInvitationRequest(
						student.getEmail(),
						student.getFirstName(),
						student.getLastName(),
						Set.of(UserRole.STUDENT.name()),
						student.getDocumentType(),
						student.getDocumentNumber(),
						metadata));

		log.info("[students] invited -- student={} email='{}' invitationId={}",
				student.getPublicUuid(), student.getEmail(), response.publicUuid());

		return new InviteStudentResponse(
				response.publicUuid(),
				response.token(),
				response.expiresAt(),
				student.getPublicUuid(),
				student.getEmail());
	}

	// ===========================================================================
	// Internals
	// ===========================================================================

	private Student loadStudent(UUID publicUuid) {
		return studentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Student", publicUuid));
	}

	private UUID pendingInvitationUuid(Student student) {
		if (student.getUserId() != null || student.getEmail() == null || student.getEmail().isBlank()) {
			return null;
		}
		return invitationRepository.findActivePendingByEmail(student.getEmail(), Instant.now())
				.map(UserInvitation::getPublicUuid)
				.orElse(null);
	}

	private void ensureDocumentAvailable(DocumentType type, String number, UUID excludeId) {
		studentRepository.findByDocumentTypeAndDocumentNumber(type, number)
				.filter(existing -> !existing.getId().equals(excludeId))
				.ifPresent(conflict -> {
					throw new ConflictException("STUDENT_DOCUMENT_TAKEN",
							"Another student in this tenant already uses "
									+ type + " " + number);
				});
	}

	private void ensureEmailAvailable(String email, UUID excludeId) {
		if (email == null) return;
		studentRepository.findByEmailIgnoreCase(email)
				.filter(existing -> !existing.getId().equals(excludeId))
				.ifPresent(conflict -> {
					throw new ConflictException("STUDENT_EMAIL_TAKEN",
							"Another student in this tenant already uses email " + email);
				});
	}

	private static String normalisedEmail(String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase();
	}

	private static boolean equalsIgnoreCaseSafe(String a, String b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		return a.equalsIgnoreCase(b);
	}

	/**
	 * Translates the constraint name embedded in a
	 * {@link DataIntegrityViolationException} into a meaningful 409.
	 * Falls back to the raw exception when we can't tell which constraint
	 * fired — the global handler will turn it into a generic conflict.
	 */
	private static ConflictException mapUniqueViolation(
			DataIntegrityViolationException ex,
			DocumentType type, String number, String email) {
		String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
		if (message.contains("uk_students_tenant_document_active")) {
			return new ConflictException("STUDENT_DOCUMENT_TAKEN",
					"Another student in this tenant already uses " + type + " " + number);
		}
		if (message.contains("uk_students_tenant_email_active")) {
			return new ConflictException("STUDENT_EMAIL_TAKEN",
					"Another student in this tenant already uses email " + email);
		}
		// Re-throw as a generic conflict; the GlobalExceptionHandler
		// already maps DataIntegrityViolationException to 409 with the
		// DATA_INTEGRITY_VIOLATION code.
		return new ConflictException("DATA_INTEGRITY_VIOLATION",
				"Operation violates a data integrity constraint");
	}

	// ===========================================================================
	// Specifications
	// ===========================================================================

	static final class Specs {
		private Specs() { }

		static Specification<Student> combine(StudentListFilters f) {
			Specification<Student> spec = Specification.where(null);
			if (f.search() != null && !f.search().isBlank()) {
				spec = spec.and(searchLike(f.search().trim()));
			}
			if (f.enrollmentStatus() != null) {
				spec = spec.and(byEnrollmentStatus(f.enrollmentStatus()));
			}
			if (f.currentSectionPublicUuid() != null
					|| f.currentAcademicYearPublicUuid() != null) {
				spec = spec.and(byActiveEnrollment(
						f.currentSectionPublicUuid(),
						f.currentAcademicYearPublicUuid()));
			}
			return spec;
		}

		private static Specification<Student> searchLike(String needle) {
			final String pattern = "%" + needle.toLowerCase() + "%";
			return (root, q, cb) -> {
				Predicate firstHit = cb.like(cb.lower(root.get("firstName")), pattern);
				Predicate lastHit = cb.like(cb.lower(root.get("lastName")), pattern);
				Predicate docHit = cb.like(cb.lower(root.get("documentNumber")), pattern);
				return cb.or(firstHit, lastHit, docHit);
			};
		}

		private static Specification<Student> byEnrollmentStatus(
				com.edushift.modules.students.entity.EnrollmentStatus status) {
			return (root, q, cb) -> cb.equal(root.get("enrollmentStatus"), status);
		}

		/**
		 * Restricts the list to students that have at least one ACTIVE
		 * {@link StudentEnrollment} matching the supplied
		 * {@code (sectionPublicUuid?, academicYearPublicUuid?)} pair.
		 *
		 * <p>Implementation note: we use an EXISTS subquery instead of
		 * a JOIN so the {@code count()} variant Spring Data issues for
		 * pagination doesn't trip on duplicate rows when a single
		 * student has multiple historical enrollments. Hibernate's
		 * tenant discriminator is applied on both the outer
		 * {@code Student} root and the inner {@code StudentEnrollment}
		 * subquery, so cross-tenant ids collapse to "no match".</p>
		 */
		private static Specification<Student> byActiveEnrollment(
				UUID sectionPublicUuid, UUID academicYearPublicUuid) {
			return (root, q, cb) -> {
				if (q == null) return cb.conjunction();
				Subquery<UUID> sub = q.subquery(UUID.class);
				var enr = sub.from(StudentEnrollment.class);
				sub.select(enr.get("id"));

				Predicate sameStudent = cb.equal(enr.get("student"), root);
				Predicate active = cb.equal(enr.get("status"),
						StudentEnrollmentStatus.ACTIVE);

				Predicate matchSection = sectionPublicUuid == null
						? cb.conjunction()
						: cb.equal(
								enr.join("section", JoinType.INNER).get("publicUuid"),
								sectionPublicUuid);
				Predicate matchYear = academicYearPublicUuid == null
						? cb.conjunction()
						: cb.equal(
								enr.join("academicYear", JoinType.INNER).get("publicUuid"),
								academicYearPublicUuid);

				sub.where(cb.and(sameStudent, active, matchSection, matchYear));
				return cb.exists(sub);
			};
		}
	}
}
