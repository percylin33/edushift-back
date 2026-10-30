package com.edushift.modules.schedule.timeslot.service;

import com.edushift.modules.academic.classrooms.repository.ClassroomRepository;
import com.edushift.modules.schedule.daytemplate.service.NonTeachingBlockResolver;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects conflicts when scheduling {@link TimeSlot} rows
 * (Sprint cierre-C / B4).
 *
 * <h3>Three conflict dimensions</h3>
 * <ol>
 *   <li><b>Teacher conflict</b> — same teacher scheduled at two
 *       overlapping slots on the same day <em>within the same
 *       academic period</em>.</li>
 *   <li><b>Classroom conflict</b> — same {@code classroom_id} (or
 *       legacy free-text {@code classroom} label when no FK) double-booked
 *       at overlapping times in the same period.</li>
 *   <li><b>Section conflict</b> — same section gets two slots that
 *       overlap in the same period. Resolved via
 *       {@code teacher_assignment.section}.</li>
 * </ol>
 *
 * <h3>Period scope (ADR-SCH-13)</h3>
 * TimeSlots hang on period-scoped {@code TeacherAssignment}s. Bimestres
 * are sequential, so the same weekly pattern across periods is a year
 * template replica — not a double booking. Conflicts only compare
 * active assignments in the candidate's academic period.
 *
 * <h3>Time overlap test</h3>
 * Two slots overlap when
 * {@code new.start < existing.end AND new.end > existing.start}.
 * We also require the same day-of-week (ISO-8601: 1=MON ... 7=SUN).
 *
 * <h3>Multi-tenant</h3>
 * Every query filters explicitly on {@code TenantContext.currentRequired()}.
 * Hibernate {@code @TenantId} is already enforced on
 * {@link TimeSlotRepository} via the entity, so this is defense-in-depth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleConflictDetector {

	private final TimeSlotRepository timeSlotRepository;
	private final ClassroomRepository classroomRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final NonTeachingBlockResolver nonTeachingBlockResolver;

	/**
	 * Throws {@link ScheduleConflictException} when any of the three
	 * conflict dimensions fires. {@code excludeSlotUuid} is the slot
	 * being updated (so it does not match itself).
	 */
	@Transactional(readOnly = true)
	public void assertNoConflicts(
			UUID teacherAssignmentUuid,
			UUID classroomUuid,
			String classroomLabel,
			Short dayOfWeek,
			LocalTime startTime,
			LocalTime endTime,
			UUID excludeSlotUuid
	) {
		UUID tenantId = TenantContext.currentRequired();
		TeacherAssignment assignment = teacherAssignmentRepository
				.findByPublicUuid(teacherAssignmentUuid)
				.orElseThrow(() -> new IllegalArgumentException(
						"Teacher assignment not found: " + teacherAssignmentUuid));
		UUID teacherUuid = assignment.getTeacher().getPublicUuid();
		UUID sectionUuid = assignment.getSection().getPublicUuid();
		UUID periodUuid = assignment.getAcademicPeriod().getPublicUuid();

		// 1. Teacher conflict: same teacher + period + day + overlapping window.
		List<TimeSlot> teacherSlots = timeSlotRepository
				.findByTenantIdAndTeacherPeriodAndDay(
						tenantId, teacherUuid, periodUuid, dayOfWeek);
		for (TimeSlot existing : teacherSlots) {
			if (excludeSlotUuid != null && existing.getPublicUuid().equals(excludeSlotUuid)) continue;
			if (overlaps(existing.getStartTime(), existing.getEndTime(), startTime, endTime)) {
				throw new ScheduleConflictException(
						ScheduleConflictException.Dimension.TEACHER,
						teacherUuid,
						existing.getPublicUuid(),
						String.format(
								"El docente ya tiene otro slot el mismo día (%s) entre %s y %s.",
								dayOfWeek,
								existing.getStartTime(),
								existing.getEndTime()));
			}
		}

		// 2. Classroom conflict (B4): same classroom_id + period + overlapping window.
		if (classroomUuid != null) {
			List<TimeSlot> classroomSlots = timeSlotRepository
					.findByTenantIdAndClassroomPeriodAndDay(
							tenantId, classroomUuid, periodUuid, dayOfWeek);
			for (TimeSlot existing : classroomSlots) {
				if (excludeSlotUuid != null && existing.getPublicUuid().equals(excludeSlotUuid)) continue;
				if (overlaps(existing.getStartTime(), existing.getEndTime(), startTime, endTime)) {
					throw new ScheduleConflictException(
							ScheduleConflictException.Dimension.CLASSROOM,
							classroomUuid,
							existing.getPublicUuid(),
							"El aula ya está reservada ese día en ese horario.");
				}
			}
		}

		// 2b. Legacy free-text classroom label (pre-B4 slots).
		if (classroomLabel != null && !classroomLabel.isBlank()) {
			List<TimeSlot> labelSlots = timeSlotRepository
					.findByTenantIdAndClassroomLabelPeriodAndDay(
							tenantId, classroomLabel, periodUuid, dayOfWeek);
			for (TimeSlot existing : labelSlots) {
				if (excludeSlotUuid != null && existing.getPublicUuid().equals(excludeSlotUuid)) continue;
				if (overlaps(existing.getStartTime(), existing.getEndTime(), startTime, endTime)) {
					throw new ScheduleConflictException(
							ScheduleConflictException.Dimension.CLASSROOM,
							null,
							existing.getPublicUuid(),
							String.format(
									"Otra sección ya reservó la etiqueta '%s' el mismo día entre %s y %s.",
									classroomLabel,
									existing.getStartTime(),
									existing.getEndTime()));
				}
			}
		}

		// 3. Section conflict: same section + period + overlapping window.
		List<TimeSlot> sectionSlots = timeSlotRepository
				.findByTenantIdAndSectionPeriodAndDay(
						tenantId, sectionUuid, periodUuid, dayOfWeek);
		for (TimeSlot existing : sectionSlots) {
			if (excludeSlotUuid != null && existing.getPublicUuid().equals(excludeSlotUuid)) continue;
			if (overlaps(existing.getStartTime(), existing.getEndTime(), startTime, endTime)) {
				throw new ScheduleConflictException(
						ScheduleConflictException.Dimension.SECTION,
						sectionUuid,
						existing.getPublicUuid(),
						"La sección ya tiene otra clase en ese horario.");
			}
		}

		// 4. Recess / lunch / assembly from the section's day template (ADR-SCH-6).
		nonTeachingBlockResolver.assertNoOverlapWithRecess(
				assignment.getSection(), dayOfWeek, startTime, endTime);
	}

	private static boolean overlaps(LocalTime aStart, LocalTime aEnd,
	                                LocalTime bStart, LocalTime bEnd) {
		// Half-open intervals: [aStart, aEnd) overlaps [bStart, bEnd)
		// iff aStart < bEnd AND bStart < aEnd.
		return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
	}
}
