package com.edushift.modules.schedule.timeslot.service.impl;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.schedule.daytemplate.dto.NonTeachingBlockItem;
import com.edushift.modules.schedule.daytemplate.dto.SuggestedPeriodItem;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import com.edushift.modules.schedule.daytemplate.service.DayScheduleTemplateService;
import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotListItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.mapper.TimeSlotMapper;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.schedule.timeslot.service.ScheduleConflictDetector;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link TimeSlotService}.
 *
 * <h3>Overlap algorithm</h3>
 * Two ranges {@code [a, b)} and {@code [c, d)} overlap iff
 * {@code a < d AND c < b}. We delegate the test to a JPQL query
 * ({@code TimeSlotRepository.findOverlapping}) so it's covered by the
 * tenant filter; the candidate's row id is excluded on update so the
 * row being modified never collides with itself.
 *
 * <h3>Why no partial unique index</h3>
 * Postgres' {@code btree_gist} extension does support
 * {@code EXCLUDE USING GIST (assignment WITH =, range WITH &&)}, but
 * (a) it requires the extension to be installed cluster-wide, (b) it
 * gives us the same protection as the JPQL probe but with a less
 * actionable error message, and (c) the FE flow is already optimistic
 * → conflict → 409 anyway. The trade-off is documented in the
 * package-info as DEBT-SCH-1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

	private final TimeSlotRepository timeSlotRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final TeacherRepository teacherRepository;
	private final SectionRepository sectionRepository;
	private final AcademicPeriodRepository periodRepository;
	private final TimeSlotMapper mapper;
	private final CurrentUserProvider currentUserProvider;
	private final ScheduleConflictDetector conflictDetector;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final DayScheduleTemplateService dayScheduleTemplateService;

	// =========================================================================
	// CRUD
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<TimeSlotListItem> listSlotsOfAssignment(UUID assignmentUuid) {
		TeacherAssignment assignment = loadAssignment(assignmentUuid);
		return timeSlotRepository.findAllByAssignmentOrdered(assignment).stream()
				.map(mapper::toListItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public TimeSlotResponse getSlot(UUID slotUuid) {
		return mapper.toResponse(loadSlot(slotUuid));
	}

	@Override
	@Transactional
	public TimeSlotResponse createSlot(UUID assignmentUuid, CreateTimeSlotRequest request) {
		TeacherAssignment assignment = loadAssignment(assignmentUuid);
		ensureAssignmentActive(assignment);

		validateTimeRange(request.startTime(), request.endTime());
		ensureNoOverlap(assignment, request.dayOfWeek(),
				request.startTime(), request.endTime(), null);

		TimeSlot slot = mapper.fromCreate(request, assignment);
		slot.setTeacherAssignment(assignment);
		assertNoConflicts(slot, null);

		TimeSlot saved = timeSlotRepository.saveAndFlush(slot);

		log.info("[schedule.time-slot] created -- publicUuid={} assignment={} day={} {}-{}",
				saved.getPublicUuid(), assignment.getPublicUuid(),
				saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public TimeSlotResponse updateSlot(UUID slotUuid, UpdateTimeSlotRequest request) {
		TimeSlot slot = loadSlot(slotUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(slot);
		}

		ensureAssignmentActive(slot.getTeacherAssignment());
		mapper.applyUpdate(request, slot);

		validateTimeRange(slot.getStartTime(), slot.getEndTime());
		ensureNoOverlap(slot.getTeacherAssignment(), slot.getDayOfWeek(),
				slot.getStartTime(), slot.getEndTime(), slot.getId());
		assertNoConflicts(slot, slot.getId());

		TimeSlot saved = timeSlotRepository.saveAndFlush(slot);
		log.info("[schedule.time-slot] updated -- publicUuid={} day={} {}-{}",
				saved.getPublicUuid(), saved.getDayOfWeek(),
				saved.getStartTime(), saved.getEndTime());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public void deleteSlot(UUID slotUuid) {
		TimeSlot slot = loadSlot(slotUuid);
		timeSlotRepository.delete(slot);
		log.info("[schedule.time-slot] deleted -- publicUuid={}", slot.getPublicUuid());
	}

	// =========================================================================
	// Reverse views
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<ScheduleSlotItem> getTeacherSchedule(UUID teacherUuid, UUID periodUuid) {
		Teacher teacher = teacherRepository.findByPublicUuid(teacherUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Teacher", teacherUuid));

		// Sprint 5 / DEBT-TEA-1: TENANT_ADMIN sees any teacher; a TEACHER caller
		// may only see their own schedule. Anti-enumeration: surface the same
		// 404 we'd return for an unknown teacher — never confirm to the caller
		// that someone else's schedule exists.
		if (!isCurrentUserAdmin()) {
			UUID callerUserPublicUuid = currentUserProvider.currentUserId()
					.orElseThrow(() -> new ResourceNotFoundException(
							"Teacher", teacherUuid));
			UUID myTeacherPublicUuid = teacherRepository
					.findByUserId(callerUserPublicUuid)
					.map(Teacher::getPublicUuid)
					.orElseThrow(() -> new ResourceNotFoundException(
							"Teacher", teacherUuid));
			if (!teacher.getPublicUuid().equals(myTeacherPublicUuid)) {
				log.warn("[schedule.time-slot] self-only guard hit -- callerUserId={} "
						+ "askedTeacher={} myTeacher={}",
						callerUserPublicUuid, teacher.getPublicUuid(), myTeacherPublicUuid);
				throw new ResourceNotFoundException("Teacher", teacherUuid);
			}
		}

		AcademicPeriod period = (periodUuid == null) ? null : periodRepository
				.findByPublicUuid(periodUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", periodUuid));

		List<TeacherAssignment> assignments = assignmentRepository
				.findAllByTeacher(teacher, period, true);
		if (assignments.isEmpty()) return List.of();

		return timeSlotRepository.findAllByAssignmentInOrdered(assignments).stream()
				.map(mapper::toTeacherScheduleItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScheduleSlotItem> getSectionSchedule(UUID sectionUuid, UUID periodUuid) {
		Section section = sectionRepository.findByPublicUuid(sectionUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Section", sectionUuid));
		AcademicPeriod period = (periodUuid == null) ? null : periodRepository
				.findByPublicUuid(periodUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", periodUuid));

		List<TeacherAssignment> assignments = assignmentRepository
				.findAllBySectionActive(section, period);
		if (assignments.isEmpty()) return List.of();

		return timeSlotRepository.findAllByAssignmentInOrdered(assignments).stream()
				.map(mapper::toSectionScheduleItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ScheduleSlotItem> getScheduleForStudent(Student student, UUID periodUuid) {
		if (student == null) {
			return List.of();
		}
		AcademicPeriod period = (periodUuid == null) ? null : periodRepository
				.findByPublicUuid(periodUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", periodUuid));
		List<StudentEnrollment> enrollments = enrollmentRepository.findActiveByStudentFetchSection(student);
		List<TeacherAssignment> assignments = new ArrayList<>();
		for (StudentEnrollment enrollment : enrollments) {
			var section = enrollment.getSection();
			if (section == null) {
				continue;
			}
			assignments.addAll(assignmentRepository.findAllBySectionActive(section, period));
		}
		if (assignments.isEmpty()) {
			return List.of();
		}
		return timeSlotRepository.findAllByAssignmentInOrdered(assignments).stream()
				.map(mapper::toPortalScheduleItem)
				.sorted(Comparator
						.comparing(ScheduleSlotItem::dayOfWeek, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(ScheduleSlotItem::startTime, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ScheduleWeekView getTeacherScheduleWeek(UUID teacherUuid, UUID periodUuid) {
		List<ScheduleSlotItem> slots = getTeacherSchedule(teacherUuid, periodUuid);
		List<NonTeachingBlockItem> blocks = collectNonTeachingForTeacher(teacherUuid, periodUuid);
		return ScheduleWeekView.of(slots, blocks);
	}

	@Override
	@Transactional(readOnly = true)
	public ScheduleWeekView getSectionScheduleWeek(UUID sectionUuid, UUID periodUuid) {
		Section section = sectionRepository.findByPublicUuid(sectionUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Section", sectionUuid));
		List<ScheduleSlotItem> slots = getSectionSchedule(sectionUuid, periodUuid);
		List<NonTeachingBlockItem> blocks = dayScheduleTemplateService
				.listHardNonTeachingBlocksForSection(section, null);
		List<SuggestedPeriodItem> periods = dayScheduleTemplateService
				.listSuggestedPeriodsForSection(section);
		var templateOpt = dayScheduleTemplateService.resolveTemplateForSection(section);
		LocalTime dayStart = templateOpt.map(DayScheduleTemplate::getDayStart).orElse(null);
		LocalTime dayEnd = templateOpt.map(DayScheduleTemplate::getDayEnd).orElse(null);
		Integer periodMinutes = templateOpt.map(DayScheduleTemplate::getPeriodMinutes).orElse(null);
		return ScheduleWeekView.of(slots, blocks, dayStart, dayEnd, periodMinutes, periods);
	}

	@Override
	@Transactional(readOnly = true)
	public ScheduleWeekView getScheduleWeekForStudent(Student student, UUID periodUuid) {
		List<ScheduleSlotItem> slots = getScheduleForStudent(student, periodUuid);
		List<NonTeachingBlockItem> blocks = new ArrayList<>();
		if (student != null) {
			List<StudentEnrollment> enrollments = enrollmentRepository.findActiveByStudentFetchSection(student);
			for (StudentEnrollment enrollment : enrollments) {
				Section section = enrollment.getSection();
				if (section != null) {
					blocks.addAll(dayScheduleTemplateService
							.listHardNonTeachingBlocksForSection(section, null));
				}
			}
		}
		return ScheduleWeekView.of(slots, dedupeBlocks(blocks));
	}

	private List<NonTeachingBlockItem> collectNonTeachingForTeacher(UUID teacherUuid, UUID periodUuid) {
		Teacher teacher = teacherRepository.findByPublicUuid(teacherUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Teacher", teacherUuid));
		AcademicPeriod period = (periodUuid == null) ? null : periodRepository
				.findByPublicUuid(periodUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", periodUuid));
		List<TeacherAssignment> assignments = assignmentRepository
				.findAllByTeacher(teacher, period, true);
		List<NonTeachingBlockItem> blocks = new ArrayList<>();
		for (TeacherAssignment assignment : assignments) {
			if (assignment.getSection() != null) {
				blocks.addAll(dayScheduleTemplateService
						.listHardNonTeachingBlocksForSection(assignment.getSection(), null));
			}
		}
		return dedupeBlocks(blocks);
	}

	private static List<NonTeachingBlockItem> dedupeBlocks(List<NonTeachingBlockItem> blocks) {
		return blocks.stream()
				.collect(java.util.stream.Collectors.toMap(
						NonTeachingBlockItem::blockPublicUuid,
						b -> b,
						(a, b) -> a,
						java.util.LinkedHashMap::new))
				.values()
				.stream()
				.toList();
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private TeacherAssignment loadAssignment(UUID publicUuid) {
		return assignmentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"TeacherAssignment", publicUuid));
	}

	private TimeSlot loadSlot(UUID publicUuid) {
		return timeSlotRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("TimeSlot", publicUuid));
	}

	private boolean isCurrentUserAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) return false;
		for (GrantedAuthority granted : auth.getAuthorities()) {
			String authority = granted.getAuthority();
			if (authority == null) continue;
			if (authority.equals("TENANT_ADMIN")) return true;
			if (authority.equals("ROLE_TENANT_ADMIN")) return true;
		}
		return false;
	}

	private static void ensureAssignmentActive(TeacherAssignment assignment) {
		if (!assignment.isActive()) {
			throw new ConflictException("ASSIGNMENT_NOT_ACTIVE",
					"Assignment " + assignment.getPublicUuid()
							+ " is soft-ended; restore or create a new assignment "
							+ "before adding/editing slots");
		}
	}

	private static void validateTimeRange(LocalTime startTime, LocalTime endTime) {
		if (startTime == null || endTime == null) {
			throw new BadRequestException("TIME_SLOT_DATE_INVERTED",
					"startTime and endTime are required");
		}
		if (!endTime.isAfter(startTime)) {
			throw new BadRequestException("TIME_SLOT_DATE_INVERTED",
					"endTime (" + endTime + ") must be strictly after startTime ("
							+ startTime + ")");
		}
	}

	private void ensureNoOverlap(TeacherAssignment assignment, Short dayOfWeek,
			LocalTime startTime, LocalTime endTime, UUID excludeId) {
		// Legacy per-assignment overlap kept for backward compatibility.
		List<TimeSlot> conflicts = timeSlotRepository.findOverlapping(
				assignment, dayOfWeek, startTime, endTime, excludeId);
		if (!conflicts.isEmpty()) {
			TimeSlot first = conflicts.get(0);
			throw new ConflictException("TIME_SLOT_OVERLAP",
					"Slot [%s, %s) on day %d overlaps with existing slot [%s, %s)"
							.formatted(startTime, endTime, dayOfWeek,
									first.getStartTime(), first.getEndTime()));
		}
	}

	/**
	 * Sprint cierre-C / B4 -- multi-dimension conflict check (teacher,
	 * classroom, section). Wired into {@link #createSlot} and
	 * {@link #updateSlot}; surfaces a structured 409 via
	 * {@link com.edushift.modules.schedule.timeslot.service.ScheduleConflictException}
	 * with the offending dimension + slotUuid so the FE can highlight
	 * the conflicting cell in the schedule grid.
	 */
	private void assertNoConflicts(TimeSlot slot, UUID excludeId) {
		conflictDetector.assertNoConflicts(
				slot.getTeacherAssignment().getPublicUuid(),
				slot.getClassroomId(),
				slot.getClassroom(),
				slot.getDayOfWeek(),
				slot.getStartTime(),
				slot.getEndTime(),
				excludeId);
	}
}
