package com.edushift.modules.students.enrollments.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.dto.EnrollmentListItem;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.dto.SectionStudentRosterItem;
import com.edushift.modules.students.enrollments.dto.WithdrawEnrollmentRequest;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.mapper.StudentEnrollmentMapper;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.enrollments.service.StudentEnrollmentService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link StudentEnrollmentService}.
 *
 * <h3>Validation pipeline (create path)</h3>
 * <ol>
 *   <li>Resolve the three anchors (student, section, year). Any missing
 *       collapses to {@code 404 RESOURCE_NOT_FOUND}.</li>
 *   <li>Reject if {@code section.academicYear != request.academicYear}
 *       — code {@code ENROLLMENT_YEAR_MISMATCH} (409).</li>
 *   <li>Reject if {@code enrolledAt} is outside
 *       {@code [year.startDate, year.endDate]} — code
 *       {@code ENROLLMENT_DATE_OUT_OF_YEAR} (409).</li>
 *   <li>Reject if there is already an ACTIVE row for {@code (student, year)}
 *       — code {@code STUDENT_ALREADY_ENROLLED} (409).</li>
 *   <li>Save and rely on the partial unique index
 *       {@code uk_student_enrollments_active} as a backstop against
 *       races; map {@link DataIntegrityViolationException} back to the
 *       same 409 code.</li>
 * </ol>
 *
 * <h3>Withdraw pipeline</h3>
 * <ol>
 *   <li>Resolve the row by public UUID — 404 otherwise.</li>
 *   <li>Idempotent no-op when the row is already in a terminal state
 *       (returns the current snapshot).</li>
 *   <li>Reject {@link StudentEnrollmentStatus#ACTIVE} as the target —
 *       code {@code INVALID_WITHDRAW_STATUS} (400).</li>
 *   <li>Reject {@code withdrawnAt &lt; enrolledAt} — code
 *       {@code VALIDATION_ERROR} (400).</li>
 *   <li>Persist the new {@code (status, withdrawnAt)} pair.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentEnrollmentServiceImpl implements StudentEnrollmentService {

	private final StudentEnrollmentRepository enrollmentRepository;
	private final StudentRepository studentRepository;
	private final SectionRepository sectionRepository;
	private final AcademicYearRepository yearRepository;
	private final StudentEnrollmentMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<EnrollmentListItem> listForStudent(UUID studentPublicUuid) {
		Student student = loadStudent(studentPublicUuid);
		return enrollmentRepository.findAllByStudent(student).stream()
				.map(mapper::toListItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SectionStudentRosterItem> listRoster(UUID sectionPublicUuid) {
		Section section = loadSection(sectionPublicUuid);
		return enrollmentRepository.findActiveBySection(section).stream()
				.map(mapper::toRosterItem)
				.toList();
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public EnrollmentResponse createEnrollment(UUID studentPublicUuid,
			CreateEnrollmentRequest request) {

		Student student = loadStudent(studentPublicUuid);
		Section section = loadSection(request.sectionPublicUuid());
		AcademicYear year = loadYear(request.academicYearPublicUuid());

		validateYearAlignment(section, year);
		validateEnrolledAtInYear(year, request.enrolledAt());
		validateNoActiveEnrollment(student, year);

		StudentEnrollment enrollment = new StudentEnrollment();
		enrollment.setStudent(student);
		enrollment.setSection(section);
		enrollment.setAcademicYear(year);
		enrollment.setEnrolledAt(request.enrolledAt());
		enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
		enrollment.setNotes(blankToNull(request.notes()));

		try {
			StudentEnrollment saved = enrollmentRepository.saveAndFlush(enrollment);
			log.info("[students.enrollments] created -- publicUuid={} student={} section={} year={} enrolledAt={}",
					saved.getPublicUuid(),
					student.getPublicUuid(),
					section.getPublicUuid(),
					year.getPublicUuid(),
					saved.getEnrolledAt());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// uk_student_enrollments_active fired — concurrent insert
			// with the same (student, year) tuple. Surface a clean 409.
			throw new ConflictException("STUDENT_ALREADY_ENROLLED",
					"Student '" + student.fullName()
							+ "' already has an active enrollment in year '"
							+ year.getName() + "'.", ex);
		}
	}

	@Override
	@Transactional
	public EnrollmentResponse withdrawEnrollment(UUID enrollmentPublicUuid,
			WithdrawEnrollmentRequest request) {

		StudentEnrollment enrollment = enrollmentRepository.findByPublicUuid(enrollmentPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"StudentEnrollment", enrollmentPublicUuid));

		if (enrollment.getStatus() != StudentEnrollmentStatus.ACTIVE) {
			// Idempotent: re-issuing withdraw on a terminal row is a no-op.
			log.info("[students.enrollments] withdraw no-op (already terminal) -- publicUuid={} status={}",
					enrollment.getPublicUuid(), enrollment.getStatus());
			return mapper.toResponse(enrollment);
		}

		validateTerminalStatus(request.status());
		validateWithdrawDateOrder(enrollment.getEnrolledAt(), request.withdrawnAt());

		enrollment.setStatus(request.status());
		enrollment.setWithdrawnAt(request.withdrawnAt());

		StudentEnrollment saved = enrollmentRepository.saveAndFlush(enrollment);
		log.info("[students.enrollments] soft-ended -- publicUuid={} student={} status={} withdrawnAt={}",
				saved.getPublicUuid(),
				saved.getStudent().getPublicUuid(),
				saved.getStatus(),
				saved.getWithdrawnAt());

		return mapper.toResponse(saved);
	}

	// =========================================================================
	// Validations
	// =========================================================================

	private void validateYearAlignment(Section section, AcademicYear year) {
		UUID sectionYearId = section.getAcademicYear().getId();
		UUID requestYearId = year.getId();
		if (!sectionYearId.equals(requestYearId)) {
			throw new ConflictException("ENROLLMENT_YEAR_MISMATCH",
					"Section belongs to year '"
							+ section.getAcademicYear().getName()
							+ "' but the request targets year '"
							+ year.getName() + "'.");
		}
	}

	private void validateEnrolledAtInYear(AcademicYear year, LocalDate enrolledAt) {
		LocalDate start = year.getStartDate();
		LocalDate end = year.getEndDate();
		if (enrolledAt.isBefore(start) || enrolledAt.isAfter(end)) {
			throw new ConflictException("ENROLLMENT_DATE_OUT_OF_YEAR",
					"enrolledAt " + enrolledAt
							+ " is outside the academic year window ["
							+ start + ", " + end + "].");
		}
	}

	private void validateNoActiveEnrollment(Student student, AcademicYear year) {
		enrollmentRepository.findActiveByStudentAndYear(student, year)
				.ifPresent(existing -> {
					throw new ConflictException("STUDENT_ALREADY_ENROLLED",
							"Student '" + student.fullName()
									+ "' already has an active enrollment in year '"
									+ year.getName() + "'.");
				});
	}

	private void validateTerminalStatus(StudentEnrollmentStatus target) {
		if (target == null || target == StudentEnrollmentStatus.ACTIVE) {
			throw new BadRequestException("INVALID_WITHDRAW_STATUS",
					"Withdraw target must be one of WITHDRAWN, TRANSFERRED, "
							+ "GRADUATED — got " + target + ".");
		}
	}

	private void validateWithdrawDateOrder(LocalDate enrolledAt, LocalDate withdrawnAt) {
		if (withdrawnAt.isBefore(enrolledAt)) {
			throw new BadRequestException("VALIDATION_ERROR",
					"withdrawnAt " + withdrawnAt
							+ " cannot be earlier than enrolledAt " + enrolledAt + ".");
		}
	}

	// =========================================================================
	// Loaders
	// =========================================================================

	private Student loadStudent(UUID publicUuid) {
		return studentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Student", publicUuid));
	}

	private Section loadSection(UUID publicUuid) {
		return sectionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Section", publicUuid));
	}

	private AcademicYear loadYear(UUID publicUuid) {
		return yearRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private static String blankToNull(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
