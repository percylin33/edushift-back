package com.edushift.modules.teachers.assignments.service.impl;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.teachers.assignments.dto.AssignmentListItem;
import com.edushift.modules.teachers.assignments.dto.AssignmentResponse;
import com.edushift.modules.teachers.assignments.dto.CreateAssignmentRequest;
import com.edushift.modules.teachers.assignments.dto.SectionTeacherItem;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.mapper.TeacherAssignmentMapper;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.assignments.service.TeacherAssignmentService;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link TeacherAssignmentService}.
 *
 * <h3>Validation pipeline (create path)</h3>
 * <ol>
 *   <li>Resolve the four anchors (teacher, section, course, period). Any
 *       missing → {@code 404 RESOURCE_NOT_FOUND} (Hibernate's tenant
 *       discriminator already turns cross-tenant ids into not-founds).</li>
 *   <li>Reject if {@code teacher.employmentStatus.isAssignable() == false}
 *       — code {@code TEACHER_NOT_ACTIVE} (409).</li>
 *   <li>Reject if {@code section.academicYear != period.academicYear}
 *       — code {@code ASSIGNMENT_YEAR_MISMATCH} (409).</li>
 *   <li>Reject if the course is not linked to the section's level via
 *       {@code course_levels} — code
 *       {@code COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL} (409).</li>
 *   <li>Reject if there is already an active row for the same 4-tuple
 *       — code {@code ASSIGNMENT_ALREADY_ACTIVE} (409).</li>
 *   <li>Save and rely on the partial unique index as a backstop against
 *       races; map a {@link DataIntegrityViolationException} back to
 *       {@code ASSIGNMENT_ALREADY_ACTIVE}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

	private final TeacherAssignmentRepository assignmentRepository;
	private final TeacherRepository teacherRepository;
	private final SectionRepository sectionRepository;
	private final CourseRepository courseRepository;
	private final AcademicPeriodRepository periodRepository;
	private final CourseLevelRepository courseLevelRepository;
	private final TeacherAssignmentMapper mapper;

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<AssignmentListItem> listForTeacher(UUID teacherPublicUuid,
			UUID periodPublicUuid, boolean activeOnly) {
		Teacher teacher = loadTeacher(teacherPublicUuid);
		AcademicPeriod period = (periodPublicUuid == null)
				? null : loadPeriod(periodPublicUuid);

		return assignmentRepository.findAllByTeacher(teacher, period, activeOnly)
				.stream()
				.map(mapper::toListItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SectionTeacherItem> listForSection(UUID sectionPublicUuid,
			UUID periodPublicUuid) {
		Section section = loadSection(sectionPublicUuid);
		AcademicPeriod period = (periodPublicUuid == null)
				? null : loadPeriod(periodPublicUuid);

		return assignmentRepository.findAllBySectionActive(section, period)
				.stream()
				.map(mapper::toSectionTeacherItem)
				.toList();
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public AssignmentResponse createAssignment(UUID teacherPublicUuid,
			CreateAssignmentRequest request) {
		Teacher teacher = loadTeacher(teacherPublicUuid);
		Section section = loadSection(request.sectionPublicUuid());
		Course course   = loadCourse(request.coursePublicUuid());
		AcademicPeriod period = loadPeriod(request.academicPeriodPublicUuid());

		validateTeacherAssignable(teacher);
		validateYearAlignment(section, period);
		validateCourseAppliesToLevel(course, section);
		validateNoActiveDuplicate(teacher, section, course, period);

		TeacherAssignment assignment = new TeacherAssignment();
		assignment.setTeacher(teacher);
		assignment.setSection(section);
		assignment.setCourse(course);
		assignment.setAcademicPeriod(period);
		assignment.setNotes(blankToNull(request.notes()));
		// assignedAt default in @PrePersist; explicit here for clarity in logs
		assignment.setAssignedAt(Instant.now());

		try {
			TeacherAssignment saved = assignmentRepository.saveAndFlush(assignment);
			log.info("[teachers.assignments] created -- publicUuid={} teacher={} section={} course={} period={}",
					saved.getPublicUuid(),
					teacher.getPublicUuid(),
					section.getPublicUuid(),
					course.getPublicUuid(),
					period.getPublicUuid());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// uk_teacher_assignments_active fired — concurrent duplicate
			// insert with the same 4-tuple. Surface a clean 409.
			throw new ConflictException("ASSIGNMENT_ALREADY_ACTIVE",
					"There is already an active assignment for this teacher, "
							+ "section, course and period.", ex);
		}
	}

	@Override
	@Transactional
	public void softEnd(UUID assignmentPublicUuid) {
		TeacherAssignment assignment = assignmentRepository
				.findByPublicUuid(assignmentPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"TeacherAssignment", assignmentPublicUuid));

		if (assignment.getUnassignedAt() != null) {
			// Idempotent: re-issuing DELETE on a soft-ended row is a no-op.
			log.info("[teachers.assignments] soft-end no-op (already ended) -- publicUuid={}",
					assignment.getPublicUuid());
			return;
		}

		assignment.setUnassignedAt(Instant.now());
		assignmentRepository.saveAndFlush(assignment);
		log.info("[teachers.assignments] soft-ended -- publicUuid={} teacher={} unassignedAt={}",
				assignment.getPublicUuid(),
				assignment.getTeacher().getPublicUuid(),
				assignment.getUnassignedAt());
	}

	// =========================================================================
	// Validations
	// =========================================================================

	private void validateTeacherAssignable(Teacher teacher) {
		if (!teacher.getEmploymentStatus().isAssignable()) {
			throw new ConflictException("TEACHER_NOT_ACTIVE",
					"Teacher '" + teacher.fullName() + "' has employment status "
							+ teacher.getEmploymentStatus()
							+ " and cannot be assigned to new sections.");
		}
	}

	private void validateYearAlignment(Section section, AcademicPeriod period) {
		UUID sectionYearId = section.getAcademicYear().getId();
		UUID periodYearId  = period.getAcademicYear().getId();
		if (!sectionYearId.equals(periodYearId)) {
			throw new ConflictException("ASSIGNMENT_YEAR_MISMATCH",
					"Section belongs to year '"
							+ section.getAcademicYear().getName()
							+ "' but period belongs to year '"
							+ period.getAcademicYear().getName()
							+ "'.");
		}
	}

	private void validateCourseAppliesToLevel(Course course, Section section) {
		AcademicLevel sectionLevel = section.getGrade().getLevel();
		boolean applies = courseLevelRepository.existsByCourseAndLevel(course, sectionLevel);
		if (!applies) {
			throw new ConflictException("COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL",
					"Course '" + course.getCode()
							+ "' is not associated with level '"
							+ sectionLevel.getCode() + "'.");
		}
	}

	private void validateNoActiveDuplicate(Teacher teacher, Section section,
			Course course, AcademicPeriod period) {
		assignmentRepository.findActiveTuple(teacher, section, course, period)
				.ifPresent(existing -> {
					throw new ConflictException("ASSIGNMENT_ALREADY_ACTIVE",
							"There is already an active assignment for this teacher, "
									+ "section, course and period.");
				});
	}

	// =========================================================================
	// Loaders
	// =========================================================================

	private Teacher loadTeacher(UUID publicUuid) {
		return teacherRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Teacher", publicUuid));
	}

	private Section loadSection(UUID publicUuid) {
		return sectionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Section", publicUuid));
	}

	private Course loadCourse(UUID publicUuid) {
		return courseRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Course", publicUuid));
	}

	private AcademicPeriod loadPeriod(UUID publicUuid) {
		return periodRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", publicUuid));
	}

	private static String blankToNull(String s) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
