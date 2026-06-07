package com.edushift.modules.schedule.timeslot.service.impl;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotListItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.mapper.TimeSlotMapper;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
