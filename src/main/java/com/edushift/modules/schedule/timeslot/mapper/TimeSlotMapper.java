package com.edushift.modules.schedule.timeslot.mapper;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotListItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse.AssignmentRef;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse.CourseRef;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse.PeriodRef;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse.SectionRef;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse.TeacherRef;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link TimeSlot}. Same convention as the rest
 * of the codebase (no MapStruct).
 */
@Component
public class TimeSlotMapper {

	public TimeSlotResponse toResponse(TimeSlot slot) {
		return new TimeSlotResponse(
				slot.getPublicUuid(),
				toAssignmentRef(slot.getTeacherAssignment()),
				slot.getDayOfWeek(),
				slot.getStartTime(),
				slot.getEndTime(),
				slot.getClassroom(),
				slot.getCreatedAt(),
				slot.getUpdatedAt()
		);
	}

	public TimeSlotListItem toListItem(TimeSlot slot) {
		return new TimeSlotListItem(
				slot.getPublicUuid(),
				slot.getDayOfWeek(),
				slot.getStartTime(),
				slot.getEndTime(),
				slot.getClassroom()
		);
	}

	public TimeSlot fromCreate(CreateTimeSlotRequest request,
			TeacherAssignment assignment) {
		TimeSlot slot = new TimeSlot();
		slot.setTeacherAssignment(assignment);
		slot.setDayOfWeek(request.dayOfWeek());
		slot.setStartTime(request.startTime());
		slot.setEndTime(request.endTime());
		slot.setClassroom(blankToNull(request.classroom()));
		return slot;
	}

	public void applyUpdate(UpdateTimeSlotRequest patch, TimeSlot slot) {
		if (patch.dayOfWeek() != null) {
			slot.setDayOfWeek(patch.dayOfWeek());
		}
		if (patch.startTime() != null) {
			slot.setStartTime(patch.startTime());
		}
		if (patch.endTime() != null) {
			slot.setEndTime(patch.endTime());
		}
		if (patch.classroom() != null) {
			slot.setClassroom(blankToNull(patch.classroom()));
		}
	}

	/**
	 * Builds a {@link ScheduleSlotItem} for the teacher-centric reverse
	 * view: {@code teacher} is intentionally {@code null} (caller
	 * already knows who they are), {@code section} is populated.
	 */
	public ScheduleSlotItem toTeacherScheduleItem(TimeSlot slot) {
		TeacherAssignment a = slot.getTeacherAssignment();
		return new ScheduleSlotItem(
				slot.getPublicUuid(),
				a.getPublicUuid(),
				slot.getDayOfWeek(),
				slot.getStartTime(),
				slot.getEndTime(),
				slot.getClassroom(),
				null,
				toScheduleCourseRef(a.getCourse()),
				toScheduleSectionRef(a.getSection()),
				toSchedulePeriodRef(a.getAcademicPeriod())
		);
	}

	/**
	 * Builds a {@link ScheduleSlotItem} for the section-centric reverse
	 * view: {@code section} is intentionally {@code null}, {@code teacher}
	 * is populated.
	 */
	public ScheduleSlotItem toSectionScheduleItem(TimeSlot slot) {
		TeacherAssignment a = slot.getTeacherAssignment();
		return new ScheduleSlotItem(
				slot.getPublicUuid(),
				a.getPublicUuid(),
				slot.getDayOfWeek(),
				slot.getStartTime(),
				slot.getEndTime(),
				slot.getClassroom(),
				toScheduleTeacherRef(a.getTeacher()),
				toScheduleCourseRef(a.getCourse()),
				null,
				toSchedulePeriodRef(a.getAcademicPeriod())
		);
	}

	// =========================================================================
	// AssignmentRef (TimeSlotResponse)
	// =========================================================================

	private static AssignmentRef toAssignmentRef(TeacherAssignment a) {
		if (a == null) return null;
		return new AssignmentRef(
				a.getPublicUuid(),
				toTeacherRef(a.getTeacher()),
				toCourseRef(a.getCourse()),
				toSectionRef(a.getSection()),
				toPeriodRef(a.getAcademicPeriod())
		);
	}

	private static TeacherRef toTeacherRef(Teacher t) {
		if (t == null) return null;
		return new TeacherRef(t.getPublicUuid(), t.getFirstName(), t.getLastName());
	}

	private static CourseRef toCourseRef(Course c) {
		if (c == null) return null;
		return new CourseRef(c.getPublicUuid(), c.getCode(), c.getName());
	}

	private static SectionRef toSectionRef(Section s) {
		if (s == null) return null;
		return new SectionRef(s.getPublicUuid(), s.getName());
	}

	private static PeriodRef toPeriodRef(AcademicPeriod p) {
		if (p == null) return null;
		return new PeriodRef(p.getPublicUuid(),
				p.getPeriodType() == null ? null : p.getPeriodType().name(),
				p.getOrdinal(), p.getName());
	}

	// =========================================================================
	// Schedule refs (ScheduleSlotItem)
	// =========================================================================

	private static ScheduleSlotItem.TeacherRef toScheduleTeacherRef(Teacher t) {
		if (t == null) return null;
		return new ScheduleSlotItem.TeacherRef(
				t.getPublicUuid(), t.getFirstName(), t.getLastName());
	}

	private static ScheduleSlotItem.CourseRef toScheduleCourseRef(Course c) {
		if (c == null) return null;
		return new ScheduleSlotItem.CourseRef(
				c.getPublicUuid(), c.getCode(), c.getName());
	}

	private static ScheduleSlotItem.SectionRef toScheduleSectionRef(Section s) {
		if (s == null) return null;
		return new ScheduleSlotItem.SectionRef(s.getPublicUuid(), s.getName());
	}

	private static ScheduleSlotItem.PeriodRef toSchedulePeriodRef(AcademicPeriod p) {
		if (p == null) return null;
		return new ScheduleSlotItem.PeriodRef(p.getPublicUuid(),
				p.getPeriodType() == null ? null : p.getPeriodType().name(),
				p.getOrdinal(), p.getName());
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		return value.isBlank() ? null : value;
	}
}
